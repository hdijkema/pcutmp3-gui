package de.zebee.mpa;

public class Track {

    private String performer;
    private String title;
    private int    trackNumber;

    private long   startSector;

    private long   endSector;

    public Track() {
        performer = "Unknown Artist";

    }

    public Track(String performer, String title, String album) {
        this.performer = performer;
        this.title = title;
    }

    public long getEndSector() {
        return endSector;
    }

    public String getPerformer() {
        return this.performer;
    }

    public long getStartSector() {
        return startSector;
    }

    public String getTitle() {
        return this.title;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setEndSector(long endSector) {
        this.endSector = endSector;
    }

    public void setPerformer(String performer) {
        this.performer = performer;
    }

    public void setStartSector(long startSector) {
        this.startSector = startSector;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
        if (title == null) {
            title = "Track " + trackNumber;
        }
    }

}
