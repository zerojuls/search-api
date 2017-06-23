package com.vivareal.search.api.model.parser;

import com.vivareal.search.api.model.query.LogicalOperator;
import com.vivareal.search.api.model.query.RelationalOperator;
import org.jparsec.Parser;
import org.jparsec.Scanners;
import org.jparsec.Terminals;
import org.jparsec.Tokens;

import java.util.function.Function;
import java.util.function.Supplier;

public class OperatorParser {

    public static final Parser<LogicalOperator> LOGICAL_OPERATOR_PARSER = get(LogicalOperator::getOperators, LogicalOperator::get);

    public static final Parser<RelationalOperator> RELATIONAL_OPERATOR_PARSER = get(RelationalOperator::getOperators, RelationalOperator::get);

    private static <T> Parser<T> get(Supplier<String[]> operators, Function<String, T> getFn) {
        Terminals OPERATORS = Terminals.operators(operators.get());
        Parser<T> OPERATOR_MAPPER = Terminals.fragment(Tokens.Tag.RESERVED).map(getFn);
        return OPERATOR_MAPPER.from(OPERATORS.tokenizer(), Scanners.WHITESPACES.optional(null));
    }
}