package de.zebee.mpa;

import java.util.ArrayList;

public class Cue {

    private String           performer;
    private String           title;
    private ArrayList<Track> tracks;

    private String           pathToMP3;

    private long             sampleCount;

    public Cue() {
        tracks = new ArrayList<Track>(40);
    }

    public Cue(String performer, String title, String album) {
        this();
        this.performer = performer;
        this.title = title;
    }

    public void addTrack(int trackNumber, Track t) {
        if (trackNumber >= 1)
            tracks.add(trackNumber - 1, t);
    }

    public void fillOutEndTrackSectors() {

        for (int i = 0; i < tracks.size() - 1; i++) {
            long endSector = tracks.get(i + 1).getStartSector();
            tracks.get(i).setEndSector(endSector);
        }

    }

    public int getNumberTracks() {
        return tracks.size();
    }

    public String getPathToMP3() {
        return this.pathToMP3;
    }

    public String getPerformer() {
        return this.performer;
    }

    public long getSampleCount() {
        return this.sampleCount;
    }

    public String getTitle() {
        return this.title;
    }

    public Track getTrack(int trackNumber) {
        return tracks.get(trackNumber);
    }

    public void setPathToMP3(String pathToMP3) {
        this.pathToMP3 = pathToMP3;
    }

    public void setPerformer(String performer) {
        this.performer = performer;
    }

    public void setSampleCount(long sampleCount) {
        this.sampleCount = sampleCount;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
