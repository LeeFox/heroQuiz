package com.vanderfox.hero

import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import com.vanderfox.hero.question.Question
import com.vanderfox.hero.user.User
import groovy.transform.CompileStatic
import org.slf4j.Logger;
import org.slf4j.LoggerFactory
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;

/**
 * This app shows how to connect to hero with Spring Social, Groovy, and Alexa.
 * @author Lee Fox and Ryan Vanderwerf
 */
@CompileStatic
public class HeroSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(HeroSpeechlet.class);




    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        session.setAttribute("playerList", new ArrayList<User>())
        LinkedHashMap<String, Question> askedQuestions = new LinkedHashMap()
        session.setAttribute("askedQuestions", askedQuestions)
        session.setAttribute("questionCounter", getNumberOfQuestions())
        session.setAttribute("score", 0)
        session.setAttribute("playerIndex", 0)
        session.setAttribute("playerCount", 0)
        session.setAttribute("state", "start")
        initializeComponents(session)

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        getWelcomeResponse(session);
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;
        Slot query = intent.getSlot("Answer")
        Slot count = intent.getSlot("Count")
        String state = session.getAttribute("state")
        log.info("Intent = ${intentName}")
        log.info("state = ${state}")
        log.info("query = ${(query == null) ? "null" : query.value}")
        log.info("count = ${(count == null) ? "null" : count.value}")
        if("stop".equalsIgnoreCase(query.value) ||
                "cancel".equals(query.value)) {
            return endGame()
        }
        if("help".equalsIgnoreCase(query.value)) {
            return getHelpResponse(session)
        }
        switch (intentName) {
            case "ResponseIntent":
                switch (state) {
                    case "verifyPlayerName":
                        verifyPlayerName(query, session)
                        break
                    case "setPlayerName":
                        setPlayerName(query, session)
                        break
                    case "askQuestion":
                        getAnswer(query, session)
                        break
                    default:
                        getHelpResponse(session)
                        break
                }
                break
            default:
                didNotUnderstand()
                break
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse(final Session session) {
        String speechText = "Welcome to Hero Quiz.  Please tell me the first players name.";
        session.setAttribute("state", "verifyPlayerName")
        askResponseFancy(speechText, speechText, "https://s3.amazonaws.com/vanderfox-sounds/test.mp3")

    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private String getQuestion(final Session session, String speechText) {
        Question question = getRandomUnaskedQuestion(session)
        session.setAttribute("lastQuestionAsked", question)

        ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
        int playerIndex = Integer.parseInt((String) session.getAttribute("playerIndex"))
        User player = playerList.get(playerIndex)
        speechText += "\n"
        speechText += player.getName() + ", "

        speechText += "\n"
        speechText += question.getText()
        speechText
    }

    private Question getRandomUnaskedQuestion(Session session) {
        LinkedHashMap<String, Question> askedQuestions = (LinkedHashMap) session.getAttribute("askedQuestions")
        Question question = getRandomQuestion(session)
        while(askedQuestions.get(question.getText()) != null) {
            question = getRandomQuestion(session)
        }
        askedQuestions.put(question.getText(), question)
        session.setAttribute("askedQuestions", askedQuestions)
        question
    }

    private Question getRandomQuestion(Session session) {
        int tableRowCount = Integer.parseInt((String) session.getAttribute("tableRowCount"))
        int questionIndex = (new Random().nextInt() % tableRowCount).abs()
        log.info("The question index is:  " + questionIndex)
        Question question = getQuestion(questionIndex)
        question
    }

    private Question getQuestion(int questionIndex) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient());
        Table table = dynamoDB.getTable("HeroQuiz");
        Item item = table.getItem("id", questionIndex);
        def questionText = item.getString("question")
        def questionAnswer = item.getString("answer")
        Question question = new Question()
        question.setText(questionText)
        question.setAnswer(questionAnswer)
        question.setIndex(questionIndex)
        question
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse verifyPlayerName(Slot query, final Session session) {
        String playerName = query.getValue()

        String speechText = ""

        if (!"last player".equalsIgnoreCase(playerName)) {
            speechText = "I heard ${playerName}.  Is that correct?."
            session.setAttribute("playerName", playerName)
            session.setAttribute("state", "setPlayerName")
        } else {
            int questionCount = Integer.parseInt((String) session.getAttribute("questionCounter"))
            speechText = "OK, I'll now ask each player ${questionCount} questions.\n"
            speechText = getQuestion(session, speechText);
            session.setAttribute("state", "askQuestion")
        }
        askResponse(speechText, speechText)

    }

        /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse setPlayerName(Slot query, final Session session) {
        String playerNameVerfication = query.getValue()

        def speechText = ""

        if ("yes".equalsIgnoreCase(playerNameVerfication)) {
            User user = new User()
            user.setName((String) session.getAttribute("playerName"))
            ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
            playerList.add(user)
            session.setAttribute("playerList", playerList)
            int playerCount = Integer.parseInt((String) session.getAttribute("playerCount"))
            session.setAttribute("playerCount", playerCount++)
            log.info("playerCount = " + playerCount)
            speechText = "OK.  Tell me the next player’s name or say Last Player to move on."
        } else {
            speechText = "Sorry about that.  Please tell me the next players name again."
        }
        session.setAttribute("state", "verifyPlayerName")
        askResponse(speechText, speechText)
    }

    private SpeechletResponse askResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Hero Quiz");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    private SpeechletResponse tellResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Hero Quiz");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        SpeechletResponse.newTellResponse(speech, card);
    }

    private SpeechletResponse askResponseFancy(String cardText, String speechText, String fileUrl) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Hero Quiz");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        log.info("making welcome audio")
        SsmlOutputSpeech fancySpeech = new SsmlOutputSpeech()
        fancySpeech.ssml = "<speak><audio src=\"${fileUrl}\"/> ${speechText}</speak>"
        log.info("finished welcome audio")
        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(fancySpeech);

        SpeechletResponse.newAskResponse(fancySpeech, reprompt, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getAnswer(Slot query, final Session session) {

        def speechText = ""
        int playerIndex = Integer.parseInt((String) session.getAttribute("playerIndex"))
        ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
        int playerCount = playerList.size()
        log.info("playerIndex:  " + playerIndex)
        log.info("playerCount:  " + playerCount)

        String searchTerm = query.getValue()
        log.info("Guessed answer is:  " + query.getValue())

        Question question = (Question) session.getAttribute("lastQuestionAsked")
        def answer = question.getAnswer()
        log.info("correct answer is:  " + answer)
        int questionCounter = Integer.parseInt((String) session.getAttribute("questionCounter"))

        if(playerIndex + 1 == playerCount) {
            questionCounter = decrementQuestionCounter(session)
        }

        if(searchTerm.toLowerCase() == answer.toLowerCase()) {
            speechText = "You got it right."
            incrementPlayerScore(session, playerIndex)
        } else {
            speechText = "You got it wrong.  You said " + query.getValue() + "But I was looking for something else.  "
        }

        playerIndex = nextPlayer(session, playerIndex)

        log.info("questionCounter:  " + questionCounter)
        log.info("playerIndex:  " + playerIndex)
        log.info("playerCount:  " + playerCount)

        if(questionCounter > 0) {
            session.setAttribute("state", "askQuestion")
            speechText = getQuestion(session, speechText);
            return askResponse(speechText, speechText)
        } else {
            if(playerIndex == 0) {
                String score = scoreGame(session)
                speechText += score
                return tellResponse(speechText, speechText)
            }
        }
    }

    private String scoreGame(Session session) {
        ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
        int highScore = 0
        boolean tiedGame = false
        User highScorer = null
        String response = "\n\n"
        for(User currentPlayer: playerList) {
            if(currentPlayer.score > highScore) {
                highScore = currentPlayer.score
                highScorer = currentPlayer
                tiedGame = false
            } else {
                if(currentPlayer.score == highScore) {
                    tiedGame = true
                }
            }
            response += "${currentPlayer.name} answered ${currentPlayer.score} correctly.\n"
        }
        if(tiedGame) {
            response += "It was a tied game."
        } else {
            response += "${highScorer.name} is the winner."
        }
        response
    }

    private int nextPlayer(Session session, int playerIndex) {
        ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
        playerIndex++
        if (playerIndex == playerList.size()) {
            playerIndex = 0
        }
        session.setAttribute("playerIndex", playerIndex)
        playerIndex
    }

    private void incrementPlayerScore(Session session, int playerIndex) {
        ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
        User player = playerList.get(playerIndex)
        player.score += 1
        playerList.set(playerIndex, player)
        session.setAttribute("playerList", playerList)
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse(Session session) {
        String speechText = ""
        String state = session.getAttribute("state")
        switch (state) {
            case "verifyPlayerName":
                speechText = "You can say stop or cancel to end the game at any time.  Right now, we are verifying player names.  Please say yes or no to verify the last player added.";
                break
            case "setPlayerName":
                speechText = "You can say stop or cancel to end the game at any time.  Right now, we are adding players to the game.  Say the next players name or say last player to move on.";
                break
            case "askQuestion":
                speechText = "You can say stop or cancel to end the game at any time.  Right now, we are in the middle of a game.  All you need to do is give your answer to the question.";
                break
            default:
                speechText = "You can say stop or cancel to end the game at any time.  Right now, we are adding players to the game.  Say the next players name or say last player to move on.";
                break
        }
        askResponse(speechText, speechText)
    }

    private SpeechletResponse didNotUnderstand() {
        String speechText = "I'm sorry.  I didn't understand what you said.  Say help me for help.";

        askResponse(speechText, speechText)
    }

    private SpeechletResponse endGame() {
        String speechText = "OK.  I will stop the game now.  Please try again soon.";

        tellResponse(speechText, speechText)
    }

    private void incrementScore(Session session) {
        int score = (int) session.getAttribute("score")
        score++
        session.setAttribute("score", score)
    }

    private int getScore(Session session) {
        (int) session.getAttribute("score")
    }

    private int decrementQuestionCounter(Session session) {
        int questionCounter = (int) session.getAttribute("questionCounter")
        questionCounter--
        session.setAttribute("questionCounter", questionCounter)
        questionCounter

    }

    private int getQuestionCounter(Session session) {
        (int) session.getAttribute("questionCounter")
    }

    private int getNumberOfQuestions() {
        InputStream stream = com.vanderfox.hero.HeroSpeechlet.class.getClassLoader()getResourceAsStream("springSocial.properties")
        final Properties properties = new Properties();
        properties.load(stream);

        def property = properties.getProperty("numberOfQuestions")
        if (!property) {
            return 2
        }
        log.info("setting number of questions from config: ${property}")
        property.toInteger()
    }

    /**
     * Initializes the instance components if needed.
     */
    private void initializeComponents(Session session) {
        AmazonDynamoDBClient amazonDynamoDBClient;
        amazonDynamoDBClient = new AmazonDynamoDBClient();
        ScanRequest req = new ScanRequest();
        req.setTableName("HeroQuiz");
        ScanResult result = amazonDynamoDBClient.scan(req)
        List quizItems = result.items
        int tableRowCount = quizItems.size()
        session.setAttribute("tableRowCount", Integer.toString(tableRowCount))
        log.info("This many rows in the table:  " + tableRowCount)
    }
}
