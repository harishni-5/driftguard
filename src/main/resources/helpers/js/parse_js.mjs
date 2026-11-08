// Parses JS/TS (plus Vue, Svelte and Astro scripts) into DriftGuard's common JSON format.
//
// Usage: node parse_js.mjs <repo_root>
// stdin:  JSON array of repo-relative paths
// stdout: one JSON object per line, one per file
//
// Only parses, never runs the audited code. Babel is vendored next to this file,
// so no npm install is needed.
import { readFileSync, writeSync } from "node:fs";
import { builtinModules } from "node:module";
import { join } from "node:path";
import { parse } from "./babel-parser.mjs";

const MAX_STRINGS_PER_FILE = 2000;
const MAX_STRING_LENGTH = 200;
const BUILTINS = new Set(builtinModules);

function isExternal(module) {
  if (!module || /^[./\\]/.test(module)) return false;
  if (/^(node|bun|deno|https?|data|virtual|file):/.test(module)) return false;
  if (/^(@\/|~\/|~~\/|#)/.test(module)) return false; // common path aliases
  return !BUILTINS.has(module) && !BUILTINS.has(module.split("/")[0]);
}

// Keeps script blocks and blanks everything else, so line numbers stay correct.
function extractScripts(source) {
  const keep = new Array(source.length);
  for (let i = 0; i < source.length; i++) keep[i] = source[i] === "\n" ? "\n" : " ";
  for (const match of source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)) {
    const start = match.index + match[0].indexOf(">") + 1;
    for (let i = 0; i < match[1].length; i++) keep[start + i] = match[1][i];
  }
  return keep.join("");
}

function extractAstroFrontmatter(source) {
  const match = source.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  return match ? "\n" + match[1] : "";
}

function pluginsFor(path) {
  const decorators = "decorators-legacy";
  if (/\.(ts|mts|cts)$/.test(path)) return ["typescript", decorators];
  if (/\.(vue|svelte|astro)$/.test(path)) return ["typescript", decorators];
  if (/\.tsx$/.test(path)) return ["typescript", "jsx", decorators];
  return ["jsx", decorators];
}

function dottedName(node) {
  if (!node) return null;
  switch (node.type) {
    case "Identifier":
      return node.name;
    case "ThisExpression":
      return "this";
    case "MemberExpression":
    case "OptionalMemberExpression": {
      if (node.computed) return null;
      const object = dottedName(node.object);
      return object && node.property?.name ? `${object}.${node.property.name}` : null;
    }
    case "JSXIdentifier":
      return node.name;
    case "JSXMemberExpression": {
      const object = dottedName(node.object);
      return object ? `${object}.${node.property.name}` : null;
    }
    default:
      return null;
  }
}

function literalString(node) {
  if (!node) return null;
  if (node.type === "StringLiteral") return node.value;
  if (node.type === "TemplateLiteral" && node.expressions.length === 0) {
    return node.quasis[0]?.value?.cooked ?? null;
  }
  return null;
}

function analyze(ast) {
  const imports = [];
  const symbols = [];
  const calls = [];
  const strings = new Set();
  const exportedNames = new Set();
  const scope = [];

  const qualified = (name) => [...scope, ...(name ? [name] : [])].join(".") || null;
  const addImport = (module, kind, line, names = []) => {
    if (typeof module === "string" && module.length > 0) {
      imports.push({ module, kind, line, names, external: isExternal(module) });
    }
  };
  const addSymbol = (name, kind, node, exported) => {
    symbols.push({
      name,
      qualifiedName: qualified(name),
      kind,
      startLine: node.loc.start.line,
      endLine: node.loc.end.line,
      exported,
    });
  };

  function walk(node, parent) {
    if (!node || typeof node.type !== "string") return;
    const line = node.loc?.start?.line ?? 0;
    let pushed = null;
    const exportedHere = parent?.type === "ExportNamedDeclaration" || parent?.type === "ExportDefaultDeclaration";

    switch (node.type) {
      case "ImportDeclaration":
        addImport(
          node.source.value,
          node.importKind === "type" ? "type" : "import",
          line,
          node.specifiers.map((s) => s.imported?.name ?? s.imported?.value ?? s.local?.name).filter(Boolean),
        );
        return; // nothing else of interest inside
      case "ExportNamedDeclaration":
        if (node.source) {
          addImport(node.source.value, "reexport", line, node.specifiers.map((s) => s.local?.name).filter(Boolean));
        } else {
          for (const s of node.specifiers ?? []) if (s.local?.name) exportedNames.add(s.local.name);
        }
        break;
      case "ExportAllDeclaration":
        addImport(node.source.value, "reexport", line);
        return;
      case "ExportDefaultDeclaration":
        if (node.declaration?.type === "Identifier") exportedNames.add(node.declaration.name);
        break;
      case "TSImportEqualsDeclaration":
        if (node.moduleReference?.type === "TSExternalModuleReference") {
          addImport(node.moduleReference.expression?.value, "require", line);
        }
        return;
      case "TSImportType":
        addImport(literalString(node.argument?.literal ?? node.argument) , "type", line);
        break;
      case "ImportExpression":
        addImport(literalString(node.source), "dynamic", line);
        break;
      case "NewExpression":
      case "CallExpression":
      case "OptionalCallExpression": {
        if (node.callee?.type === "Import") {
          addImport(literalString(node.arguments?.[0]), "dynamic", line);
        } else {
          const callee = dottedName(node.callee);
          if (callee) {
            calls.push({ caller: qualified(), callee, line });
            if (callee === "require" || callee === "require.resolve") {
              addImport(literalString(node.arguments?.[0]), "require", line);
            }
          }
        }
        break;
      }
      case "JSXOpeningElement": {
        const callee = dottedName(node.name);
        if (callee && /^[A-Z]/.test(callee)) calls.push({ caller: qualified(), callee, line });
        break;
      }
      case "AssignmentExpression": {
        const target = dottedName(node.left);
        if (target === "module.exports") {
          if (node.right?.type === "Identifier") exportedNames.add(node.right.name);
          for (const property of node.right?.properties ?? []) {
            if (property.key?.name) exportedNames.add(property.value?.name ?? property.key.name);
          }
        } else if (target?.startsWith("exports.") || target?.startsWith("module.exports.")) {
          if (node.right?.type === "Identifier") exportedNames.add(node.right.name);
          exportedNames.add(target.split(".").pop());
        }
        break;
      }
      case "FunctionDeclaration":
        if (node.id?.name) {
          addSymbol(node.id.name, "function", node, exportedHere && scope.length === 0);
          pushed = node.id.name;
        }
        break;
      case "ClassDeclaration":
        if (node.id?.name) {
          addSymbol(node.id.name, "class", node, exportedHere && scope.length === 0);
          pushed = node.id.name;
        }
        break;
      case "ClassMethod":
        if (node.key?.name && node.kind !== "constructor") {
          addSymbol(node.key.name, "method", node, false);
          pushed = node.key.name;
        }
        break;
      case "VariableDeclarator":
        if (
          node.id?.type === "Identifier" &&
          ["ArrowFunctionExpression", "FunctionExpression"].includes(node.init?.type)
        ) {
          addSymbol(node.id.name, "function", node, false);
          pushed = node.id.name;
        }
        break;
      case "StringLiteral":
        if (
          node.value.length >= 2 &&
          node.value.length <= MAX_STRING_LENGTH &&
          !node.value.includes("\n") &&
          strings.size < MAX_STRINGS_PER_FILE
        ) {
          strings.add(node.value);
        }
        break;
    }

    if (pushed) scope.push(pushed);
    for (const key of Object.keys(node)) {
      if (key === "loc" || key === "extra" || key === "leadingComments" || key === "trailingComments" || key === "innerComments") continue;
      const value = node[key];
      if (Array.isArray(value)) {
        for (const item of value) if (item && typeof item.type === "string") walk(item, node);
      } else if (value && typeof value.type === "string") {
        walk(value, node);
      }
    }
    if (pushed) scope.pop();
  }

  walk(ast.program, null);

  // `export const a = () => {}` puts the declarator two levels below the export.
  for (const statement of ast.program.body) {
    if (statement.type === "ExportNamedDeclaration" && statement.declaration?.type === "VariableDeclaration") {
      for (const declarator of statement.declaration.declarations) {
        if (declarator.id?.name) exportedNames.add(declarator.id.name);
      }
    }
  }
  for (const symbol of symbols) {
    if (!symbol.qualifiedName.includes(".") && exportedNames.has(symbol.name)) symbol.exported = true;
  }

  return { imports, symbols, calls, strings: [...strings].sort() };
}

// Last resort for files Babel can't parse at all, such as Flow or plain broken code.
// Rough, but missing an import here would wrongly call a package unused.
function scanImports(source) {
  const imports = [];
  const patterns = [
    /\bfrom\s+["']([^"'\n]+)["']/g,
    /\bimport\s+["']([^"'\n]+)["']/g,
    /\b(?:require|import)\s*\(\s*["']([^"'\n]+)["']/g,
  ];
  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      const line = source.slice(0, match.index).split("\n").length;
      imports.push({ module: match[1], kind: "import", line, names: [], external: isExternal(match[1]) });
    }
  }
  return imports;
}

function parseFile(root, path) {
  const record = { path, language: "javascript", parseError: null, imports: [], symbols: [], calls: [], strings: [] };
  let source = "";
  try {
    source = readFileSync(join(root, path), "utf8");
    if (/\.(vue|svelte)$/.test(path)) source = extractScripts(source);
    if (/\.astro$/.test(path)) source = extractAstroFrontmatter(source);
    const ast = parse(source, {
      sourceType: "unambiguous",
      errorRecovery: true,
      allowReturnOutsideFunction: true,
      allowAwaitOutsideFunction: true,
      allowImportExportEverywhere: true,
      allowUndeclaredExports: true,
      allowNewTargetOutsideFunction: true,
      allowSuperOutsideMethod: true,
      plugins: pluginsFor(path),
    });
    Object.assign(record, analyze(ast));
    // Errors Babel recovered from: the facts above are still usable, but say so.
    if (ast.errors?.length) record.parseError = `recovered: ${ast.errors[0].message}`.slice(0, 500);
  } catch (error) {
    record.parseError = `${error.name}: ${error.message}`.slice(0, 500);
    record.imports = scanImports(source);
  }
  return record;
}

function writeAll(text) {
  const buffer = Buffer.from(text, "utf8");
  let offset = 0;
  while (offset < buffer.length) {
    try {
      offset += writeSync(1, buffer, offset);
    } catch (error) {
      if (error.code !== "EAGAIN") throw error;
    }
  }
}

const root = process.argv[2];
const paths = JSON.parse(readFileSync(0, "utf8"));
for (const path of paths) writeAll(JSON.stringify(parseFile(root, path)) + "\n");
