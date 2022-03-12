package fr.kage.nexus3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

@SpringBootApplication
public class Nexus3ExportApplication
        implements ApplicationRunner
{

    @Value("${nexus3.threads}")
    private int threads;

    @Value("${nexus3.useThread}")
    private boolean useThread;

    public static void main(String[] args)
    {
        SpringApplication.run(Nexus3ExportApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args)
    {
        if (args.getNonOptionArgs().size() < 2) {
            System.out.println("Usage:");
            System.out.println("\tnexus3 http://url.to/nexus3 repositoryId [localPath]");
            System.exit(1);
        }

        String url = args.getNonOptionArgs().get(0);
        String repoId = args.getNonOptionArgs().get(1);
        String downloadPath = args.getNonOptionArgs().size() >= 3 ? args.getNonOptionArgs().get(2) : null;

        Properties credentials = loadCredentials();
        boolean authenticate = Boolean.parseBoolean(credentials.getProperty("authenticate", "false"));
        String username = removeTrailingQuotes(credentials.getProperty("username", null));
        String password = removeTrailingQuotes(credentials.getProperty("password", null));
        if (useThread) {
            new DownloadRepository(url, repoId, downloadPath, authenticate, username, password, threads).start();
        }
        else {
            new DownloadRepositorySingle(url, repoId, downloadPath, authenticate, username, password);
        }
    }

    private Properties loadCredentials()
    {
        Properties properties = new Properties();

        File credentialsFile = new File(Paths.get("").toAbsolutePath().toFile(), "credentials.properties");

        if (!credentialsFile.exists()) {
            return properties;
        }

        try {
            properties.load(new FileInputStream(credentialsFile));
        }
        catch (IOException e) {
            System.err.println("Credentials file " + credentialsFile.getAbsolutePath() + " could not be found or is malformed!");
            System.exit(1);
        }

        return properties;
    }

    private String removeTrailingQuotes(String value)
    {
        String result;
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            result = value.substring(1, value.length() - 1);
        }
        else {
            result = value;
        }

        return result;
    }
}
