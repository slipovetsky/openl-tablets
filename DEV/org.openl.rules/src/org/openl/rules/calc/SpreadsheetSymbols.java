package org.openl.rules.calc;

public enum SpreadsheetSymbols {

    /** cell name indicating return statement */
    TYPE_DELIMITER(":"),
    TILDE("~"),
    ASTERISK("*");

    private final String symbols;

    SpreadsheetSymbols(String symbols) {
        this.symbols = symbols;
    }

    @Override
    public String toString() {
        return symbols;
    }

}
