package de.zebee.mpa.util;

import java.io.IOException;

/**
 * @author Sebastian Gesemann
 */
public interface JunkHandler {

    public void write(int bite) throws IOException;

    public void endOfJunkBlock() throws IOException;

}
