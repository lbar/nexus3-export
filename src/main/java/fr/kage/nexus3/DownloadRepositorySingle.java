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
import java.nio.file.FileAlreadyExistsException;
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

public class DownloadRepositorySingle
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadRepositorySingle.class);

    private final String url;
    private final String repositoryId;
    private Path downloadPath;

    private final boolean authenticate;
    private final String username;
    private final String password;

    private RestTemplate restTemplate;

    public DownloadRepositorySingle(String url, String repositoryId, String downloadPath, boolean authenticate, String username,
            String password)
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
        download();
    }

    public void download()
    {
        try {
            if (downloadPath == null) {
                downloadPath = Files.createTempDirectory("nexus3");
            }
            else if (!downloadPath.toFile().exists()) {
                Files.createDirectories(downloadPath);
            }
            else if (!downloadPath.toFile().isDirectory() || !downloadPath.toFile().canWrite()) {
                throw new IOException("Not a writable directory: " + downloadPath);
            }

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
            String currenToken;
            String continueToken = null;
            DownloadAssetsTask downloadAssetsTask;
            while (true) {
                downloadAssetsTask = new DownloadAssetsTask(continueToken);
                currenToken = continueToken;
                continueToken = downloadAssetsTask.run();
                if (Objects.equals(currenToken, continueToken)) {
                    return ;
                }
                if (continueToken == null) {
                    return ;
                }
            }
        }
        catch (IOException e) {
            LOGGER.error("Unable to create/use directory for local data: " + downloadPath, e);
        }
    }


    private class DownloadAssetsTask
    {

        private final String continuationToken;

        public DownloadAssetsTask(String continuationToken)
        {
            this.continuationToken = continuationToken;
        }

        public String run()
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
                return null;
            }

            final Assets assets = assetsEntity.getBody();
            assert assets != null;
            assets.getItems().forEach(item -> new DownloadItemTask(item).run());
            LOGGER.info("Download of {} assets completed", assets.getItems().size());
            return assets.getContinuationToken();
        }
    }

    private class DownloadItemTask
    {

        private final Item item;

        public DownloadItemTask(Item item)
        {
            this.item = item;
        }

        public void run()
        {
            LOGGER.debug("Downloading asset <{}>", item.getDownloadUrl());

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
            }
            catch( FileAlreadyExistsException _ignore) {
                LOGGER.info("Asset <{}> already exists, skipping", item.getPath());
            }
            catch (IOException e) {
                LOGGER.error("Failed to download asset <" + item.getDownloadUrl() + ">", e);
            }
        }
    }
}
