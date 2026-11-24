package com.example.geminitp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

public class TsundereManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final File dataFile;
	private Set<String> tsundereUsers;
	
	public TsundereManager(File dataFile) {
		this.dataFile = dataFile;
		this.tsundereUsers = new HashSet<>();
		load();
	}
	
	public boolean isTsundereMode(String username) {
		return tsundereUsers.contains(username);
	}
	
	public boolean addUser(String username) {
		if (tsundereUsers.add(username)) {
			save();
			return true;
		}
		return false;
	}
	
	public boolean removeUser(String username) {
		if (tsundereUsers.remove(username)) {
			save();
			return true;
		}
		return false;
	}
	
	public Set<String> getTsundereUsers() {
		return new HashSet<>(tsundereUsers);
	}
	
	private void load() {
		if (!dataFile.exists()) {
			return;
		}
		
		try (FileReader reader = new FileReader(dataFile)) {
			Type type = new TypeToken<Set<String>>(){}.getType();
			Set<String> loaded = GSON.fromJson(reader, type);
			if (loaded != null) {
				tsundereUsers = loaded;
			}
		} catch (IOException e) {
			GeminiTpMod.LOGGER.error("Failed to load tsundere users", e);
		}
	}
	
	private void save() {
		try {
			dataFile.getParentFile().mkdirs();
			try (FileWriter writer = new FileWriter(dataFile)) {
				GSON.toJson(tsundereUsers, writer);
			}
		} catch (IOException e) {
			GeminiTpMod.LOGGER.error("Failed to save tsundere users", e);
		}
	}
}
