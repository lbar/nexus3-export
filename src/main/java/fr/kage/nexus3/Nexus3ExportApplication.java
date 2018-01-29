package fr.kage.nexus3;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class Nexus3ExportApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(Nexus3ExportApplication.class, args);
	}


	@Override
	public void run(String... args) throws Exception {
		if (args.length > 0) {
			if (args.length >= 2) {
				String url = args[0];
				String repoId = args[1];
				String downloadPath = args.length == 3 ? args[2] : null;
				new DownloadRepository(url, repoId, downloadPath).start();
				return;
			}
			else
				System.out.println("Missing arguments for download.");
		}
		else
			System.out.println("No specified argument.");

		System.out.println("Usage:");
		System.out.println("\tnexus3 http://url.to/nexus3 repositoryId [localPath]");
		System.exit(1);
	}
}
