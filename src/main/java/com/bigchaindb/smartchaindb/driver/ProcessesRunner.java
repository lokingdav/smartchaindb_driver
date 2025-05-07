package com.bigchaindb.smartchaindb.driver;

import java.io.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProcessesRunner {
    public static void main(String[] args) {
        int numProcesses = getNumProcesses(args);
        
        try {
            startProcesses(numProcesses);
            calculateOverallThroughput();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error while launching processes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> getCommand() {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(System.getProperty("java.class.path")); // Use the same classpath
        command.add(BigchainDBJavaDriver.class.getName());
        return command;
    }

    public static void startProcesses(int numProcesses) throws IOException, InterruptedException {
        List<String> workerCmd = getCommand();
        List<Process> processes = new ArrayList<>(numProcesses);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process process : processes) {
                if (process.isAlive()) {
                    process.destroy();
                    System.out.println("Terminated subprocess: " + process);
                }
            }
            System.out.println("All subprocesses terminated.");
        }));

        for (int i = 0; i < numProcesses; i++) {
            ProcessBuilder processBuilder = new ProcessBuilder(workerCmd);

            // Optionally, set the working directory if needed
            // processBuilder.directory(new File("path/to/working/directory"));

            // Redirect output and error streams to the parent process
            processBuilder.inheritIO();

            Process process = processBuilder.start();
            processes.add(process);

            System.out.println("Started BigchainDBJavaDriver process " + (i + 1) + " of " + numProcesses);
        }

        // Wait for all processes to complete
        for (int i = 0; i < processes.size(); i++) {
            int exitCode = processes.get(i).waitFor();
            System.out.println("BigchainDBJavaDriver process " + (i + 1) + " of " + numProcesses + " finished with exit code: " + exitCode);
        }

        System.out.println("All BigchainDBJavaDriver processes have completed.");
    }

    private static int getNumProcesses(String[] args) {
        int num = 1;
        if (args.length > 0) {
            try {
                int parsed = Integer.parseInt(args[0]);
                if (parsed > 0) {
                    num = parsed;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number of processes, defaulting to 1");
            }
        }
        return num;
    }

    private static void calculateOverallThroughput() {
        long earliestStart = Long.MAX_VALUE;
        long latestEnd = Long.MIN_VALUE;
        int totalTransactions = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader("processor_metrics.log"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(", ");
                long startTime = Long.parseLong(parts[2].split("=")[1]);
                long endTime = Long.parseLong(parts[3].split("=")[1]);
                int transactions = Integer.parseInt(parts[1].split("=")[1]);

                earliestStart = Math.min(earliestStart, startTime);
                latestEnd = Math.max(latestEnd, endTime);
                totalTransactions += transactions;
            }
        } catch (IOException e) {
            System.err.println("Error reading processor metrics: " + e.getMessage());
            return;
        }

        double elapsedTime = (latestEnd - earliestStart) / 1_000_000_000.0;
        double throughput = totalTransactions / elapsedTime;

        System.out.println("Overall Throughput Calculation:");
        System.out.println("Total Transactions: " + totalTransactions);
        System.out.println("Elapsed Time: " + elapsedTime + " seconds");
        System.out.println("Throughput: " + throughput + " transactions/second");
    }
}
