package fr.kage.nexus3;

public class Item {

	private String downloadUrl;
	private String path;
	private String id;
	private String repository;
	private String format;
	private Checksum checksum;


	public String getDownloadUrl() {
		return downloadUrl;
	}


	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}


	public String getPath() {
		return path;
	}


	public void setPath(String path) {
		this.path = path;
	}


	public String getId() {
		return id;
	}


	public void setId(String id) {
		this.id = id;
	}


	public String getRepository() {
		return repository;
	}


	public void setRepository(String repository) {
		this.repository = repository;
	}


	public String getFormat() {
		return format;
	}


	public void setFormat(String format) {
		this.format = format;
	}


	public Checksum getChecksum() {
		return checksum;
	}


	public void setChecksum(Checksum checksum) {
		this.checksum = checksum;
	}
}
