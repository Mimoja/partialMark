package de.mimoja;


import java.util.*;

class MDDocument{
    DocumentParser mDocumentRoot;

    LinkedList<String> lines;

    public MDDocument(String input){
        //TODO linesplit test: ""test \nfoo \rbar \n\rlorem\r\nipsum"" -> 6
        //TODO fix empty lines
        input = input.replace("\n\r","\n");
        input = input.replace("\r", "\n");


        lines = new LinkedList<>(Arrays.asList(input.split("\n")));
        int size = lines.size();

        mDocumentRoot = new DocumentParser();

        for(int i = 0; i < size ; i++) {
            HeadlineParser headlineParser = new HeadlineParser();
            IndentCodeBlockParser indentCodeBlockParser = new IndentCodeBlockParser();
            ThematicBreakParser thematicBreakParser = new ThematicBreakParser();
            SetextHeadlineParser setextHeadlineParser = new SetextHeadlineParser();
            setextHeadlineParser.parent = mDocumentRoot.getLastOpen();
            TextParser textParser = new TextParser();
            EmptyLineParser emptyLineParser = new EmptyLineParser();

                 if(headlineParser.parse(512, lines)){mDocumentRoot.getLastOpen().addChild(headlineParser);}
            else if(setextHeadlineParser.parse(512, lines)){mDocumentRoot.getLastOpen().addChild(setextHeadlineParser);}
            else if(thematicBreakParser.parse(512, lines)){mDocumentRoot.getLastOpen().addChild(thematicBreakParser);}
            else if(emptyLineParser.parse(512, lines)){mDocumentRoot.getLastOpen().addChild(emptyLineParser);}
            else if(indentCodeBlockParser.parse(612, lines)){mDocumentRoot.getLastOpen().addChild(indentCodeBlockParser);}
            else if(textParser.parse(512, lines)){mDocumentRoot.getLastOpen().addChild(textParser);}
            else {
                System.out.println("Textparser rejected. This should never happen!");
                lines.removeFirst();
            }
        }

        mDocumentRoot.print();
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

    void addChild(AParser child){
        if(children == null){
            children = new ArrayList<>();
        }
        children.add(child);
    }

    void removeChild(AParser child){
        if(children == null){
            return;
        }
        children.remove(child);
    }

    //TODO validate
    AParser getLastOpen(){
        if(children == null){
            if(open){
                return this;
            }
        } else {
            if(!children.isEmpty()) {
                AParser lastChild = children.get(children.size() - 1);
                if (lastChild.open) {
                    return lastChild.getLastOpen();
                }
            }
            if (open) {
                return this;
            }
        }
        return null;
    }

    AParser getPreviousChild(){
        if(children == null){
            return null;
        }
        if(children.isEmpty()){
            return null;
        }
        // get latest entry
        return children.get(children.size()-1);
    }

    void print(){
        print(0);
    }
    void print(int indent){
        if(children == null){
            return;
        }
        for(AParser child: children){
            for(int i = 0; i < indent; i++) System.out.print(" ");
            System.out.println(child.getClass().getCanonicalName());
            if(child.children != null){
                child.print(indent+4);
            }
        }
    }

    abstract boolean parse(int currentPosition, LinkedList<String> lines);

    protected char readChar(LinkedList<String> lines){
        char current = peakChar(lines);
        currentPosition++;
        return current;
    }

    protected char peakChar(LinkedList<String> lines){

        if(currentLine >= lines.size()){
            return '\0';
        }

        String line = lines.get(currentLine);
        if(currentPosition >= line.length()){
            currentLine++;
            return '\n';
        }

        return line.charAt(currentPosition);
    }

}

class DocumentParser extends AParser {

    @Override
    boolean parse(int currentPosition, LinkedList<String> lines) {
        return false;
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
    private State currentState = State.Indentation;

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
                            contentEnd = contentStart;
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
                    content = lines.removeFirst().substring(contentStart, contentEnd);
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

class EmptyLineParser extends AParser{
    private enum State{
        Whitespace,
        Accept,
        Reject,
    };

    State currentState = State.Whitespace;
    int whitespaceCount = 0;

    @Override
    boolean parse(int currentPosition, LinkedList<String> lines) {
        char nextChar;
        while(true) {
            switch (currentState) {
                case Whitespace:
                    nextChar = readChar(lines);
                    if (nextChar == ' ') {
                        whitespaceCount++;
                    } else if (nextChar == '\n') {
                        currentState = State.Accept;
                    } else {
                        currentState = State.Reject;
                    }
                    break;
                case Accept:
                    System.out.println("[EmptyLine] Space: "+whitespaceCount);
                    lines.removeFirst();
                    open = false;
                    return true;
                case Reject:
                    return false;
            }
        }
    }
}

class TextParser extends AParser {
    private enum State{
        Indentation,
        Content,
        StripAfter,
        Accept,
        Reject,
    };

    State currentState = State.Indentation;

    List<Character> breakingCharacters;

    int indentation = 0;
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
                    } else if (nextChar == '\n') {
                      currentState = State.Reject;
                    } else {
                        this.contentStart = this.currentPosition-1;
                        contentEnd = contentStart;
                        currentState = State.Content;
                    }
                    break;
                case Content:
                    nextChar = readChar(lines);
                    if (nextChar == ' '){
                        currentState = State.StripAfter;
                    } else if (nextChar == '\n') {
                        currentState = State.Accept;
                        contentEnd = this.currentPosition-1;
                    } else {
                        contentEnd = this.currentPosition;
                    }
                    break;
                case StripAfter:
                    nextChar = readChar(lines);
                    if (nextChar == '\n'){
                        currentState = State.Accept;
                    } else if (nextChar != ' '){
                        contentEnd = this.currentPosition;
                        currentState = State.Content;
                    }
                    break;
                case Accept:
                    this.start = currentPosition;
                    this.end = this.currentPosition;
                    content = lines.removeFirst().substring(contentStart, contentEnd);
                    System.out.println("[Text] Indentation: "+indentation+" Content: \""+content+"\"");
                    open = false;
                    return true;
                case Reject:
                    return false;
            }
        }
    }
}

class ThematicBreakParser extends AParser {
    private enum State{
        Indentation,
        Counting,
        Accept,
        Reject,
    };
    State currentState = State.Indentation;

    List<Character> breakingCharacters;

    public ThematicBreakParser(){
        breakingCharacters = new LinkedList<>();
        char[] chars ={'-', '_', '*'};
        for(char c : chars)
            breakingCharacters.add(c);
    }
    int indentation = 0;
    int breakCounter = 0;
    Character breakItem = null;


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
                    }else if(breakingCharacters.contains(nextChar)){
                        breakItem = nextChar;
                        breakCounter++;
                        currentState = State.Counting;
                    }else{
                        currentState = State.Reject;
                    }
                    break;
                case Counting:
                    nextChar = readChar(lines);
                    if(breakItem.equals(nextChar)) {
                        breakCounter++;
                    } else if (nextChar == ' '){
                        // Do nothing, just consume
                        currentState = State.Counting;
                    } else if (nextChar == '\n') {
                        if(breakCounter >= 3) {
                            currentState = State.Accept;
                        } else {
                            currentState = State.Reject;
                        }
                    } else {
                        currentState = State.Reject;
                    }
                    break;
                case Accept:
                    this.start = currentPosition;
                    this.end = this.currentPosition;
                    System.out.println("[Break] Indentation: "+indentation+" Symbol: '"+breakItem+"' BreakItems: "+breakCounter);
                    open = false;
                    lines.removeFirst();
                    return true;
                case Reject:
                    return false;
            }
        }
    }
}

class SetextHeadlineParser extends AParser {
    private enum State {
        Indentation,
        Counting,
        Accept,
        Reject
    }

    private State currentState = State.Indentation;
    public int indentation = 0;
    public int breakCounter = 0;
    public Character levelCharacter = null;

    @Override
    boolean parse(int currentPosition, LinkedList<String> lines) {
        char nextChar;
        while (true) {
            switch (currentState) {
                case Indentation:
                    if(!(parent.getPreviousChild() instanceof TextParser)){
                        currentState = State.Reject;
                        break;
                    }
                    nextChar = readChar(lines);
                    if (nextChar == ' ') {
                        indentation++;
                        if (indentation >= 4) {
                            currentState = State.Reject;
                        }
                    } else if (nextChar == '-' || nextChar == '=') {
                        breakCounter++;
                        levelCharacter = nextChar;
                        currentState = State.Counting;
                    } else {
                        currentState = State.Reject;
                    }
                    break;
                case Counting:
                    nextChar = readChar(lines);
                    if (levelCharacter.equals(nextChar)) {
                        breakCounter++;
                    } else if (nextChar == '\n') {
                        if ((levelCharacter.equals('-') && breakCounter >= 2) || levelCharacter.equals('='))
                        {
                            currentState = State.Accept;
                        } else {
                            currentState = State.Reject;
                        }
                    } else {
                        currentState = State.Reject;
                    }
                    break;
                case Accept:
                    this.start = currentPosition;
                    this.end = this.currentPosition;
                    System.out.println("[SetextHeadline] Indentation: " + indentation + " Symbol: '" + levelCharacter + "' Level: " + (levelCharacter.equals('=')?"1":"2") + " Count: "+ breakCounter);
                    open = false;
                    lines.removeFirst();
                    AParser content = parent.getPreviousChild();
                    parent.removeChild(content);
                    addChild(content);
                    return true;
                case Reject:
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

    private State currentState = State.Indentation;


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
                        contentEnd = contentStart;
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
                    content = lines.removeFirst().substring(contentStart, contentEnd);
                    System.out.println("[Headline] Indentation: "+indentation+" Level: "+level+" Content: \""+content+"\"");
                    open = false;
                    return true;
                case Reject:
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

                "    CodeBlock\n"                    + // [Code] "CodeBlock"
                "    CodeBlock *with* style \n"      + // [Code] "CodeBlock *with* special chars"
                "    CodeBlock    \n"                + // [Code] "CodeBlock    "
                "        CodeBlockWithIndentation\n" + // [Code] "    CodeBlock with indentation"
                "   NotSoCodeblock\n"                + // false

                "--\n"                               + // false
                "-+-+\n"                             + // false
                "-+--\n"                             + // false
                "-_-\n"                              + // false
                "    ---\n"                          + // false
                "---\n"                              + // [Break] Symbol: '-' + Count == 3
                "___\n"                              + // [Break] Symbol: '_' + Count == 3
                "***\n"                              + // [Break] Symbol: '*' + Count == 3
                "- - - \n"                           + // [Break] Symbol: '-' + Count == 3
                "-     -      -      -\n"            + // [Break] Symbol: '-' + Count == 4
                "-------------\n"                    + // [Break] Symbol: '-' + Count == 13
                " **  * ** * ** * **\n"              + // [Break] Symbol: '*' + Count == 11 + Intend = 1
                "   ---\n"                           + // [Break] Symbol: '-' + Count == 3 + Intend = 3

                "Text\n===\n"                        + // [SetextHeadline] Level=1 + Count = 3;
                "Text\n---\n"                        + // [SetextHeadline] Level=2 + Count = 3;
                "Text\n=\n"                          + // [SetextHeadline] Level=1 + Count = 1;
                "Text\n-\n\n"                        + // false
                "---\n---\n"                         + // false

                " \n"                                + // Emptyline Space = 1
                "    \n"                             + // Emptyline Space = 4

        "");
    }
}
