# Nexus 3 export tool

## Purpose

The purpose of this tool is to export a repository from Sonatype Nexus 3. Since version 3, the repositories stored in Nexus are not browsable by filesystem. The tool uses the Nexus API to extract all assets of a given repository.

## Usage

Program takes 2 required arguments plus 1 optional:
* URL of the Nexus repository
* Id of the repository in Nexus (e.g. _releases_)
* (Optional) The local directory for repository to export. If no one is provided, a directory is creeated in the user temp directory

### From IDE

You can create a Run Configuration by starting the `Nexus3ExportApplication` class. You have to add to specify the previous program arguments.

### From CLI

Package the application as a JAR with Maven tool:

```bash
$ mvn clean package # (or use provided mvnw tool if you do not have local maven cli tool)
```

Then launch the tool:

```bash
$ java -jar target\nexus3-export-1.0.jar http://url.to/nexus3 releases
```

## License

The tool is published under MIT license.