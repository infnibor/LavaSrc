package com.github.topi314.lavasrc.spotify;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpotifyTokenTester {
    public static void main(String[] args) {
        // Suppress Apache HttpClient warnings (Java Util Logging)
        Logger.getLogger("org.apache.http").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.http.wire").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.http.headers").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.http.client").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.http.impl.client").setLevel(Level.SEVERE);

        // Suppress SLF4J SimpleLogger warnings (if used)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

        System.out.println("Testing retrieval of secret and version from player...");

        try {
            SpotifyTokenTracker.SecretAndVersion result = SpotifyTokenTracker.requestSecretAndVersion();
            if (result == null) {
                System.out.println("Failed to retrieve secret and version!");
                // Add stack trace for better diagnostics
                new Exception("Missing secret and version").printStackTrace(System.out);
                System.out.println("Exited with code 1. Run with --stacktrace for details.");
                System.exit(1);
            } else {
                System.out.println("Secret (byte array): " + Arrays.toString(result.secret));
                // Print secret as hex string
                StringBuilder hex = new StringBuilder();
                for (byte b : result.secret) {
                    hex.append(String.format("%02X ", b));
                }
                System.out.println("Secret (hex): " + hex.toString().trim());
                System.out.println("Version: " + result.version);
                System.out.println("Test completed successfully.");
            }
        } catch (IOException e) {
            System.out.println("Error during test (IOException): " + e.getMessage());
            e.printStackTrace(System.out);
            System.out.println("Exited with code 2. Run with --stacktrace for details.");
            System.exit(2);
        } catch (Exception e) {
            System.out.println("Critical error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            System.out.println("Exited with code 3. Run with --stacktrace for details.");
            System.exit(3);
        } catch (Throwable t) {
            System.out.println("Unexpected error (" + t.getClass().getName() + "): " + t.getMessage());
            t.printStackTrace(System.out);
            System.out.println("Exited with code 10. Run with --stacktrace for details.");
            System.exit(10);
        }
    }
}
