package de.mimoja;


import sun.util.resources.cldr.en.CurrencyNames_en;

import java.util.*;

class MDDocument{
    AParser mDocumentRoot;

    LinkedList<String> lines;

    public MDDocument(String input){
        //TODO linesplit test: ""test \nfoo \rbar \n\rlorem\r\nipsum"" -> 6
        //TODO fix empty lines
        input = input.replace("\n\r","\n");
        input = input.replace("\r", "\n");
        lines = new LinkedList<>(Arrays.asList(input.split("\n")));
        int size = lines.size();


        for(int i = 0; i < size ; i++) {
            HeadlineParser headlineParser = new HeadlineParser();
            IndentCodeBlockParser indentCodeBlockParser = new IndentCodeBlockParser();

                 if(headlineParser.parse(0, lines)){}
            else if(indentCodeBlockParser.parse(0, lines)){}
            lines.removeFirst();
        }
    }

}

abstract class AParser {
    AParser parent;
    ArrayList<AParser> children;

    int start;
    int currentPosition = 0;
    int currentLine = 0;
    int end;

    boolean open = true;


    abstract boolean parse(int currentPosition, LinkedList<String> lines);

    protected char readChar(LinkedList<String> lines){

        if(currentLine >= lines.size()){
            return '\0';
        }

        String line = lines.get(currentLine);
        if(currentPosition >= line.length()){
            currentLine++;
            return '\n';
        }
        char current = line.charAt(currentPosition);
        currentPosition++;
        return current;
    }
}

class IndentCodeBlockParser extends AParser {

    private enum State{
        Indentation,
        Content,
        Accept,
        Reject,
    }
    public int indentation = 0;
    private State currentState;

    public IndentCodeBlockParser(){
        currentState = State.Indentation;
    }

    private int contentStart;
    private int contentEnd;

    public String content;

    @Override
    boolean parse(int currentPosition, LinkedList<String> lines) {
        while(true) {
            char nextChar;
            switch (currentState) {
                case Indentation:
                    nextChar = readChar(lines);
                    if (nextChar == ' ') {
                        indentation++;
                        if (indentation == 4) {
                            contentStart = this.currentPosition;
                            currentState = State.Content;
                        }
                    } else {
                        currentState = State.Reject;
                    }
                    break;
                case Content:
                    nextChar = readChar(lines);
                    if (nextChar == '\n') {
                        currentState = State.Accept;
                    } else {
                        contentEnd = this.currentPosition;
                    }
                    break;
                case Accept:
                    this.start = currentPosition;
                    this.end = this.currentPosition;
                    content = lines.getFirst().substring(contentStart, contentEnd);
                    System.out.println("[Code] Content: \""+content+"\"");
                    open = false;
                    return true;
                case Reject:
                    //System.out.println("[Code] Reject!");
                    return false;
            }
        }
    }

}

class HeadlineParser extends AParser {

    private enum State{
        Indentation,
        HeadlineLevel,
        StripBefore,
        Content,
        StripAfter,
        Accept,
        Reject,
    }

    private State currentState;

    public HeadlineParser(){
        currentState = State.Indentation;
    }

    public int indentation = 0;
    public int level = 0;

    private int contentStart;
    private int contentEnd;

    public String content;


    @Override
    boolean parse(int currentPosition, LinkedList<String> lines) {
        char nextChar;
        while(true) {
            switch (currentState) {
                case Indentation:
                    nextChar = readChar(lines);
                    if(nextChar == ' '){
                        indentation++;
                        if(indentation >= 4){
                            currentState = State.Reject;
                        }
                    }else if(nextChar == '#'){
                        level++;
                        currentState = State.HeadlineLevel;
                    }else{
                        currentState = State.Reject;
                    }
                    break;
                case HeadlineLevel:
                    nextChar = readChar(lines);
                    if(nextChar == '#') {
                        level++;
                        if(level > 6){
                            currentState = State.Reject;
                        }
                    } else if (nextChar == ' '){
                        currentState = State.StripBefore;
                    } else if (nextChar == '\n') {
                        currentState = State.Accept;
                    } else {
                        currentState = State.Reject;
                    }
                    break;
                case StripBefore:
                    nextChar = readChar(lines);
                    if (nextChar == '\n'){
                        currentState = State.Accept;
                    } else if (nextChar != ' '){
                        contentStart = this.currentPosition-1;
                        currentState = State.Content;
                    }
                    break;
                case Content:
                    nextChar = readChar(lines);
                    if (nextChar == ' ' || nextChar == '#'){
                        currentState = State.StripAfter;
                    } else if (nextChar == '\n') {
                        currentState = State.Accept;
                    }else {
                        contentEnd = this.currentPosition;
                    }
                    break;
                case StripAfter:
                    nextChar = readChar(lines);
                    if (nextChar == '\n'){
                        currentState = State.Accept;
                    } else if (nextChar != ' ' && nextChar != '#'){
                        contentEnd = this.currentPosition;
                        currentState = State.Content;
                    }
                    break;
                case Accept:
                    this.start = currentPosition;
                    this.end = this.currentPosition;
                    content = lines.getFirst().substring(contentStart, contentEnd);
                    System.out.println("[Headline] Indentation: "+indentation+" Level: "+level+" Content: \""+content+"\"");
                    open = false;
                    return true;
                case Reject:
                    //System.out.println("[Headline] Reject!");
                    return false;
            }
        }
    }
}

public class Main {

    public static void main(String[] args) {
        // HeadlineTest
        new MDDocument(""+
                "# Headline\n"                       + // [Headline] "Headline"
                "## SecondLevelHeadline\n"           + // [Headline] "SecondLevelHeadline" + level == 2
                "## ClosedHeadline ##\n"             + // [Headline] "ClosedHeadline"
                "##      Stripped Whitespace     \n" + // [Headline] "Stripped Whitespace"
                "   ## IntendedHeadline\n"           + // [Headline] "Intended headline" + intend == 3
                "# \n"                               + // [Headline] ""
                "#\n"                                + // [Headline] ""
                "####### NotAHeadline\n"             + // false
                "#NoHeadline\n"                      + // false
                "     # Actually a Codeblock\n"      + // false
                " \n"                                + // false
                "    "                               + // false
                "    CodeBlock\n"                    + // [Code] "CodeBlock"
                "    CodeBlock *with* style \n"      + // [Code] "CodeBlock *with* special chars"
                "    CodeBlock    \n"                + // [Code] "CodeBlock    "
                "        CodeBlock with indentation\n" // [Code] "    CodeBlock with indentation"
        );
    }
}
