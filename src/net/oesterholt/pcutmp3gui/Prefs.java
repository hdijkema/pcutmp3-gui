package net.oesterholt.pcutmp3gui;

import java.util.prefs.Preferences;

public class Prefs {
	
	public static String copyright() {
		return "v1.0 2013";
	}
	
	private Preferences _prefs;
	
	public boolean minimized() {
		return _prefs.getBoolean("minimized", false);
	}
	
	public void setMinimized(boolean b) {
		_prefs.putBoolean("minimized", b);
	}
	
	public int width() {
		return _prefs.getInt("width", 700);
	}
	
	public void setWidth(int v) {
		_prefs.putInt("width", v);
	}
	
	public int height () {
		return _prefs.getInt("height", 400);
	}
	
	public void setHeight(int v) {
		_prefs.putInt("height", v);
	}
	
	public int x() {
		return _prefs.getInt("x", 100);
	}
	
	public void setX(int v) {
		_prefs.putInt("x", v);
	}
	
	public int y() {
		return _prefs.getInt("y", 100);
	}
	
	public void setY(int v) {
		_prefs.putInt("y", v);
	}
	
	public String getCueLoc() {
		return _prefs.get("cueloc", "");
	}
	
	public void setCueLoc(String l) {
		_prefs.put("cueloc", l);
		
	}

	public Prefs() {
		_prefs = Preferences.userNodeForPackage(Prefs.class);
	}

}
