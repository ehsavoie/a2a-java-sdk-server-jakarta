package org.a2aproject.sdk.itk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        String httpPort = "10102";
        String grpcPort = "11002";

        for (int i = 0; i < args.length; i++) {
            if ("--httpPort".equals(args[i]) && i + 1 < args.length) {
                httpPort = args[++i];
            } else if ("--grpcPort".equals(args[i]) && i + 1 < args.length) {
                grpcPort = args[++i];
            }
        }

        Path scriptPath = Path.of("target", "wildfly", "bin", "standalone.sh");
        if (!scriptPath.toFile().exists()) {
            System.err.println("standalone.sh not found at " + scriptPath.toAbsolutePath());
            System.exit(1);
        }

        Path tmpDir = Path.of("target", "wildfly", "standalone", "tmp");
        if (Files.exists(tmpDir)) {
            new ProcessBuilder("chmod", "-R", "u+rwx", tmpDir.toString())
                    .inheritIO()
                    .start()
                    .waitFor();
            new ProcessBuilder("rm", "-rf", tmpDir.toString())
                    .inheritIO()
                    .start()
                    .waitFor();
        }

        configureLogging();

        List<String> command = new ArrayList<>();
        command.add(scriptPath.toAbsolutePath().toString());
        command.add("-Djboss.http.port=" + httpPort);
        command.add("-Djboss.grpc.port=" + grpcPort);
        command.add("--stability=preview");

        ProcessBuilder pb = new ProcessBuilder(command)
                .inheritIO()
                .directory(new File("."));

        Process process = pb.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroyForcibly()));

        System.exit(process.waitFor());
    }

    private static void configureLogging() throws IOException, InterruptedException {
        Path cliPath = Path.of("target", "wildfly", "bin", "jboss-cli.sh");
        if (!cliPath.toFile().exists()) {
            System.err.println("jboss-cli.sh not found, skipping logging configuration");
            return;
        }

        String[] loggers = {
            "org.a2aproject.sdk.server.requesthandlers",
            "org.a2aproject.sdk.server.events",
            "org.a2aproject.sdk.transport.rest.handler",
            "org.wildfly.a2a.jakarta.common"
        };

        StringBuilder cliScript = new StringBuilder();
        cliScript.append("embed-server --server-config=standalone.xml\n");
        for (String logger : loggers) {
            cliScript.append("/subsystem=logging/logger=").append(logger).append(":add(level=DEBUG)\n");
        }
        cliScript.append("stop-embedded-server\n");

        Path cliScriptFile = Path.of("target", "configure-logging.cli");
        Files.writeString(cliScriptFile, cliScript.toString());

        ProcessBuilder pb = new ProcessBuilder(
                cliPath.toAbsolutePath().toString(),
                "--file=" + cliScriptFile.toAbsolutePath()
        ).inheritIO().directory(new File("."));

        int exitCode = pb.start().waitFor();
        if (exitCode != 0) {
            System.err.println("Warning: logging configuration failed with exit code " + exitCode);
        } else {
            System.out.println("Configured DEBUG logging for SDK packages");
        }
    }
}
