package fr.kage.nexus3;

import java.util.Collection;


public class Assets {

	private Collection<Item> items;
	private String continuationToken;


	public Collection<Item> getItems() {
		return items;
	}


	public void setItems(Collection<Item> items) {
		this.items = items;
	}


	public String getContinuationToken() {
		return continuationToken;
	}


	public void setContinuationToken(String continuationToken) {
		this.continuationToken = continuationToken;
	}
}
