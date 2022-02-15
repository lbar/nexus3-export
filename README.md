# Nexus 3 export tool

## Purpose

The purpose of this tool is to export a repository from Sonatype Nexus 3. Since version 3, the repositories stored in Nexus are not browsable by filesystem. The tool uses the Nexus API to extract all assets of a given repository. It also supports Basic Authentication, which is useful for repositories requiring authentication.

## Usage

Program takes 2 required commandline arguments plus 1 optional command line argument:
* URL of the Nexus repository
* Id of the repository in Nexus (e.g. _releases_)
* (Optional) The local directory for repository to export. If no one is provided, a directory is created in the user temp directory. if 
  the directory does not exist, it will be created.

Program invoke 10 threads to download assets. you can pass `--nexus3.threads=<num>` to change the number of threads.

### Authentication

If the Nexus repository requires authentication, specify the username and password in `credentials.properties`. Also set `authenticate` to `true`.

The authentication will then be used for both the Nexus API and to download artifacts from the repository. Make sure the provided user account is allowed to access the Rest API!

**Note** If the password contains special characters (such as "="), place double quotes around the entire password.

### Running from IDE

You can create a Run Configuration by starting the `Nexus3ExportApplication` class. You have to add to specify the previous program arguments.

### Running from CLI

Package the application as a JAR with Maven tool:

```bash
$ mvn clean package # (or use provided mvnw tool if you do not have local maven cli tool)
```

Then launch the tool:

```bash
$ java -jar target/nexus3-export-1.0.jar http://url.to/nexus3 releases
```

## License

The tool is published under MIT license.