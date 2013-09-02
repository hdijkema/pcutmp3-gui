package de.zebee.mpa;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Cue {

    private String           performer;
    private String           title;
    private ArrayList<Track> tracks;
    private ObservableList<Track> o_tracks;

    private String           pathToMP3;

    private long             sampleCount;

    public Cue() {
        tracks = new ArrayList<Track>(40);
        o_tracks = FXCollections.observableArrayList(tracks);
    }

    public Cue(String performer, String title, String album) {
        this();
        this.performer = performer;
        this.title = title;
    }
    
    public void clear() {
    	o_tracks.clear();
    	title = "";
    	performer = "";
    }
    
    public ObservableList<Track> getObservable() {
    	return o_tracks;
    }

    public void addTrack(int trackNumber, Track t) {
        if (trackNumber >= 1)
            o_tracks.add(trackNumber - 1, t);
        
        int i;
        for(i = 0; i < o_tracks.size(); i++) {
        	o_tracks.get(i).setTrackNumber(i+1);
        }
    }
    
    public void removeTrack(int trackNumber) {
    	if (trackNumber >= 1) {
    		o_tracks.remove(trackNumber - 1);
    	}
        int i;
        for(i = 0; i < o_tracks.size(); i++) {
        	o_tracks.get(i).setTrackNumber(i+1);
        }
    }

    public void fillOutEndTrackSectors() {

        for (int i = 0; i < tracks.size() - 1; i++) {
            long endSector = tracks.get(i + 1).getStartSector();
            o_tracks.get(i).setEndSector(endSector);
        }

    }

    public int getNumberTracks() {
        return o_tracks.size();
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
        return o_tracks.get(trackNumber);
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

	public void save(File file) throws IOException {
		BufferedWriter wrt = new BufferedWriter(new FileWriter(file));
		wrt.write("TITLE \""+this.title.trim()+"\"\n");
		wrt.write("PERFORMER \""+this.performer.trim()+"\"\n");
		wrt.write("FILE \""+this.pathToMP3+"\" MP3\n");
		int i;
		for (i = 0; i < o_tracks.size(); i++) {
			Track t = o_tracks.get(i);
			int nr = i + 1;
			String snr = "";
			if (nr < 10) { snr = "0"; }
			snr += nr;
			wrt.write("  TRACK " + snr + " AUDIO\n");
			wrt.write("    TITLE \"" + t.getTitle().trim() + "\"\n");
			if (!t.getPerformer().trim().equals("")) {
				wrt.write("    PERFORMER \"" + t.getPerformer().trim() + "\"\n");
			}
			wrt.write("    INDEX 01 " + t.getLength() + "\n");
		}
		wrt.close();
	}

}
