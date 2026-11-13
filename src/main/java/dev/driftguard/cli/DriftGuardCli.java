package dev.driftguard.cli;

import dev.driftguard.AuditPipeline;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "driftguard",
        mixinStandardHelpOptions = true,
        version = "DriftGuard " + AuditPipeline.VERSION,
        description = "Audits codebases for AI drift.",
        subcommands = {AuditCommand.class, AskCommand.class, DiffCommand.class, EvalCommand.class, RagEvalCommand.class, DoctorCommand.class})
public final class DriftGuardCli {

    public static void main(String[] args) {
        System.exit(new CommandLine(new DriftGuardCli()).execute(args));
    }
}
