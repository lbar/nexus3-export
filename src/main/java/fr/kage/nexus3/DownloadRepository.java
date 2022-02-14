package fr.kage.nexus3;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class DownloadRepository
        implements Runnable
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadRepository.class);

    private final String url;
    private final String repositoryId;
    private Path downloadPath;

    private final boolean authenticate;
    private final String username;
    private final String password;

    private RestTemplate restTemplate;
    private ExecutorService executorService;

    private final AtomicLong assetProcessed = new AtomicLong();
    private final AtomicLong assetFound = new AtomicLong();

    public DownloadRepository(String url, String repositoryId, String downloadPath, boolean authenticate, String username, String password)
    {
        this.url = requireNonNull(url);
        this.repositoryId = requireNonNull(repositoryId);
        this.downloadPath = downloadPath == null ? null : Paths.get(downloadPath);
        this.authenticate = authenticate;
        this.username = username;
        this.password = password;
        if (url.startsWith("https://")) {
            SSLUtilities.trustAllHostnames();
            SSLUtilities.trustAllHttpsCertificates();
        }
    }

    public void start()
    {
        try {
			if (downloadPath == null) {
				downloadPath = Files.createTempDirectory("nexus3");
			}
			else if (!downloadPath.toFile().isDirectory() || !downloadPath.toFile().canWrite()) {
				throw new IOException("Not a writable directory: " + downloadPath);
			}

            LOGGER.info("Starting download of Nexus 3 repository in local directory {}", downloadPath);
            executorService = Executors.newFixedThreadPool(10);

            if (authenticate) {
                LOGGER.info("Configuring authentication for Nexus 3 repository");

                // Set auth for RestTemplate to retrieve list of assets
                RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
                restTemplate = restTemplateBuilder.basicAuthentication(username, password).build();

                // Set auth for Java to download individual assets using url.openStream();
                Authenticator.setDefault(new Authenticator()
                {
                    protected PasswordAuthentication getPasswordAuthentication()
                    {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                });
            }
            else {
                restTemplate = new RestTemplate();
            }

            executorService.submit(this);
            executorService.awaitTermination(30, TimeUnit.SECONDS);
            executorService.shutdown();
        }
        catch (IOException e) {
            LOGGER.error("Unable to create/use directory for local data: " + downloadPath);
        }
        catch (InterruptedException e) {
            // ignore it
        }
    }

    @Override
    public void run()
    {
        checkState(executorService != null, "Executor not initialized");
        executorService.submit(new DownloadAssetsTask(null));
    }

    void notifyProgress()
    {
        LOGGER.info("Downloaded {} assets on {} found", assetProcessed.get(), assetFound.get());
    }

    private class DownloadAssetsTask
            implements Runnable
    {

        private final String continuationToken;

        public DownloadAssetsTask(String continuationToken)
        {
            this.continuationToken = continuationToken;
        }

        @Override
        public void run()
        {
            LOGGER.info("Retrieving available assets to download");
            UriComponentsBuilder getAssets = UriComponentsBuilder.fromHttpUrl(url)
                    .pathSegment("service", "rest", "v1", "assets")
                    .queryParam("repository", repositoryId);
			if (continuationToken != null) {
				getAssets = getAssets.queryParam("continuationToken", continuationToken);
			}

            final ResponseEntity<Assets> assetsEntity;

            try {
                assetsEntity = restTemplate.getForEntity(getAssets.build().toUri(), Assets.class);
            }
            catch (Exception e) {
                LOGGER.error("Error retrieving available assets to download", e);
                LOGGER.error("Error retrieving available assets to download. Please check if you've specified the correct url and " +
						"repositoryId in the command line and -if authentication is needed- username and password in the credentials.properties file");
                executorService.shutdownNow();
                return;
            }

            final Assets assets = assetsEntity.getBody();
            assert assets != null;
            if (assets.getContinuationToken() != null) {
                executorService.submit(new DownloadAssetsTask(assets.getContinuationToken()));
            }

            assetFound.addAndGet(assets.getItems().size());
            notifyProgress();
            assets.getItems().forEach(item -> executorService.submit(new DownloadItemTask(item)));
        }
    }

    private class DownloadItemTask
            implements Runnable
    {

        private final Item item;

        public DownloadItemTask(Item item)
        {
            this.item = item;
        }

        @Override
        public void run()
        {
            LOGGER.info("Downloading asset <{}>", item.getDownloadUrl());

            try {
                Path assetPath = downloadPath.resolve(item.getPath());
                Files.createDirectories(assetPath.getParent());
                final URI downloadUri = URI.create(item.getDownloadUrl());
                int tryCount = 1;
                while (tryCount <= 3) {
                    try (InputStream assetStream = downloadUri.toURL().openStream()) {
                        Files.copy(assetStream, assetPath);
                        final HashCode hash = com.google.common.io.Files.asByteSource(assetPath.toFile()).hash(Hashing.sha1());
						if (Objects.equals(hash.toString(), item.getChecksum().getSha1())) {
							break;
						}
                        tryCount++;
                        LOGGER.info("Download failed, retrying");
                    }
                }
                assetProcessed.incrementAndGet();
                notifyProgress();
            }
            catch (IOException e) {
                LOGGER.error("Failed to download asset <" + item.getDownloadUrl() + ">", e);
            }
        }
    }
}
