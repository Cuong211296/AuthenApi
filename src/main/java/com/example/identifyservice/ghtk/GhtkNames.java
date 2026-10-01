package com.example.identifyservice.ghtk;

/** Translates our location names to the spelling GHTK expects. */
public final class GhtkNames {
    private GhtkNames() {
    }

    /**
     * The single place to adapt our province names (shipping table / checkout select) to GHTK's spelling.
     * Identity for now; verify against the real API with a real token and map any mismatch here.
     */
    public static String province(String ourName) {
        return ourName;
    }
}
