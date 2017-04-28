package com.vivareal.search.api.parser;

import org.jparsec.*;

public class FieldParser {

    // TODO check if we need to block fields starting with special chars like "_"
    protected static final Parser<Field> SIMPLE_KEYWORD_PARSER = Scanners.IDENTIFIER.map(Field::new).cast();

    public static Parser<Field> get() {
        return SIMPLE_KEYWORD_PARSER;
    }

}
