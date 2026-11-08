"""Parses Python files into DriftGuard's common JSON format.

Usage: python parse_python.py <repo_root>
stdin:  JSON array of repo-relative paths
stdout: one JSON object per line, one per file

Only parses, never imports or runs the audited code. Standard library only.
Needs Python 3.10+ (for sys.stdlib_module_names).
"""
import ast
import json
import os
import re
import sys

MAX_STRINGS_PER_FILE = 2000
MAX_STRING_LENGTH = 200


def read_source(path):
    if path.endswith(".ipynb"):
        with open(path, encoding="utf-8") as handle:
            notebook = json.load(handle)
        lines = []
        for cell in notebook.get("cells", []):
            if cell.get("cell_type") != "code":
                continue
            source = cell.get("source", [])
            text = source if isinstance(source, str) else "".join(source)
            for line in text.splitlines():
                # IPython magics and shell escapes are not valid Python.
                stripped = line.lstrip()
                lines.append("" if stripped.startswith(("%", "!", "?")) else line)
            lines.append("")
        return "\n".join(lines)
    with open(path, "rb") as handle:
        return handle.read()  # bytes, so ast honours PEP 263 encoding comments


def is_external(module):
    return module.split(".")[0] not in sys.stdlib_module_names


def dotted_name(node):
    parts = []
    while isinstance(node, ast.Attribute):
        parts.append(node.attr)
        node = node.value
    if isinstance(node, ast.Name):
        parts.append(node.id)
        return ".".join(reversed(parts))
    return None


class Visitor(ast.NodeVisitor):
    def __init__(self):
        self.imports = []
        self.symbols = []
        self.calls = []
        self.strings = set()
        self.scope = []  # list of (name, kind)
        self.dunder_all = None

    def qualified(self, name=None):
        parts = [entry[0] for entry in self.scope]
        if name:
            parts.append(name)
        return ".".join(parts) if parts else None

    def visit_Import(self, node):
        for alias in node.names:
            self.imports.append({
                "module": alias.name, "kind": "import", "line": node.lineno,
                "names": [], "external": is_external(alias.name),
            })

    def visit_ImportFrom(self, node):
        names = [alias.name for alias in node.names if alias.name != "*"]
        if node.level:
            module = "." * node.level + (node.module or "")
            external = False
        else:
            module = node.module or ""
            external = is_external(module)
        self.imports.append({
            "module": module, "kind": "from", "line": node.lineno,
            "names": names, "external": external,
        })

    def add_symbol(self, node, kind):
        in_function = any(entry[1] == "function" or entry[1] == "method" for entry in self.scope)
        private = any(entry[0].startswith("_") for entry in self.scope) or node.name.startswith("_")
        self.symbols.append({
            "name": node.name,
            "qualifiedName": self.qualified(node.name),
            "kind": kind,
            "startLine": node.lineno,
            "endLine": getattr(node, "end_lineno", node.lineno) or node.lineno,
            "exported": not in_function and not private,
        })

    def visit_function(self, node):
        in_class = bool(self.scope) and self.scope[-1][1] == "class"
        kind = "method" if in_class else "function"
        self.add_symbol(node, kind)
        self.scope.append((node.name, kind))
        self.generic_visit(node)
        self.scope.pop()

    visit_FunctionDef = visit_function
    visit_AsyncFunctionDef = visit_function

    def visit_ClassDef(self, node):
        self.add_symbol(node, "class")
        self.scope.append((node.name, "class"))
        self.generic_visit(node)
        self.scope.pop()

    def visit_Call(self, node):
        name = dotted_name(node.func)
        if name:
            self.calls.append({"caller": self.qualified(), "callee": name, "line": node.lineno})
            if name in ("importlib.import_module", "import_module", "__import__") and node.args:
                first = node.args[0]
                if isinstance(first, ast.Constant) and isinstance(first.value, str):
                    module = first.value
                    if module and not module.startswith("."):
                        self.imports.append({
                            "module": module, "kind": "dynamic", "line": node.lineno,
                            "names": [], "external": is_external(module),
                        })
        self.generic_visit(node)

    def visit_Assign(self, node):
        if not self.scope and any(isinstance(t, ast.Name) and t.id == "__all__" for t in node.targets):
            if isinstance(node.value, (ast.List, ast.Tuple)):
                self.dunder_all = {
                    item.value for item in node.value.elts
                    if isinstance(item, ast.Constant) and isinstance(item.value, str)
                }
        self.generic_visit(node)

    def visit_Constant(self, node):
        value = node.value
        if (isinstance(value, str) and 2 <= len(value) <= MAX_STRING_LENGTH and "\n" not in value
                and len(self.strings) < MAX_STRINGS_PER_FILE):
            self.strings.add(value)


IMPORT_LINE = re.compile(r"^\s*(?:import\s+([\w.]+(?:\s*,\s*[\w.]+)*)|from\s+([\w.]+)\s+import\b)", re.MULTILINE)


def scan_imports(source):
    """Last resort for files ast can't parse, such as Python 2 code.

    Rough, but missing an import here would wrongly call a package unused.
    """
    if isinstance(source, bytes):
        source = source.decode("utf-8", errors="replace")
    imports = []
    for match in IMPORT_LINE.finditer(source):
        line = source.count("\n", 0, match.start()) + 1
        modules = match.group(1).split(",") if match.group(1) else [match.group(2)]
        for module in (m.strip() for m in modules):
            if not module.startswith("."):
                imports.append({"module": module, "kind": "import", "line": line,
                                "names": [], "external": is_external(module)})
    return imports


def parse_file(root, relative):
    record = {"path": relative, "language": "python", "parseError": None,
              "imports": [], "symbols": [], "calls": [], "strings": []}
    source = b""
    try:
        source = read_source(os.path.join(root, relative))
        tree = ast.parse(source, filename=relative)
        visitor = Visitor()
        visitor.visit(tree)
        if visitor.dunder_all is not None:
            for symbol in visitor.symbols:
                if "." not in symbol["qualifiedName"]:
                    symbol["exported"] = symbol["name"] in visitor.dunder_all
        record.update(imports=visitor.imports, symbols=visitor.symbols,
                      calls=visitor.calls, strings=sorted(visitor.strings))
    except Exception as error:  # one bad file must never kill the whole run
        record["parseError"] = f"{type(error).__name__}: {error}"[:500]
        record["imports"] = scan_imports(source)
    return record


def main():
    sys.setrecursionlimit(5000)
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    root = sys.argv[1]
    for relative in json.load(sys.stdin):
        # ASCII escapes keep a lone surrogate (from a badly encoded string or file name) from crashing the write.
        sys.stdout.write(json.dumps(parse_file(root, relative), ensure_ascii=True) + "\n")


if __name__ == "__main__":
    main()
