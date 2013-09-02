package de.zebee.mpa;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Track {

    private final SimpleStringProperty performer;
    private final SimpleStringProperty title;
    private final SimpleIntegerProperty trackNumber;
    private final SimpleStringProperty album;
    private final SimpleStringProperty point;

    private long   startSector;
    private long   endSector;

    public Track() {
    	performer = new SimpleStringProperty("");
    	title = new SimpleStringProperty();
    	trackNumber = new SimpleIntegerProperty();
    	album = new SimpleStringProperty();
    	point = new SimpleStringProperty();
    }

    public Track(String performer, String title, String album) {
    	this.performer = new SimpleStringProperty(performer);
    	this.title = new SimpleStringProperty(title);
    	this.album = new SimpleStringProperty(album);
    	this.point = new SimpleStringProperty();
    	trackNumber = new SimpleIntegerProperty();
    }

    public long getEndSector() {
        return endSector;
    }

    public String getPerformer() {
        return this.performer.get();
    }

    public long getStartSector() {
        return startSector;
    }

    public String getTitle() {
        return this.title.get();
    }

    public int getTrackNumber() {
        return trackNumber.get();
    }
    
    public String getPoint() {
    	return this.point.get();
    }
    
    public void setPoint(String s) {
    	this.point.set(s);;
    }

    public void setEndSector(long endSector) {
        this.endSector = endSector;
    }

    public void setPerformer(String performer) {
        this.performer.set(performer);
    }

    public void setStartSector(long startSector) {
        this.startSector = startSector;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber.set(trackNumber);
        if (title.get() == null) {
            title.set("Track " + trackNumber);
        }
    }

}
