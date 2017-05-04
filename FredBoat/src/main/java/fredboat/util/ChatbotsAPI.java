package fredboat.util;

import com.google.code.chatterbotapi.ChatterBotSession;
import com.google.code.chatterbotapi.ChatterBotThought;
import fredboat.commandmeta.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by napster on 04.05.17.
 * <p>
 * Provides access to a bunch of chat bots.
 */
public class ChatbotsAPI {

    //clean the input of some code words, which for example we don't want Mitsuku to react to
    //treat as case insensitive and regex so don't add any weird regex characters to be filtered
    //reference: http://www.square-bear.co.uk/aiml
    private static final String[] WORD_BLACKLIST = {"BATTLEDOME", "FIGHT", "BLACKJACK", "CALENDAR", "PERSONALITY TEST", "TEXTHANGMAN",
            "High Roller", "HOROSCOPE", "KNOCK KNOCK", "LUCKYSLOTS", "NUMBERDROP", "5CARDPOKER",
            "TICTACTOE", "WORDPLAY"};


    private static final List<PandoraBot> pandoraBots = new ArrayList<>();

    static {
        //Mitsuku http://www.mitsuku.com/
        pandoraBots.add(new PandoraBot("f326d0be8e345a13", "kakko"));
        //Fake James T. Kirk http://sheepridge.pandorabots.com/pandora/talk?botid=fef38cb4de345ab1
        pandoraBots.add(new PandoraBot("fef38cb4de345ab1", "sheepridge"));
        // source: https://www.pandorabots.com/botmaster/en/mostactive
        pandoraBots.add(new PandoraBot("b0a6a41a5e345c23", "www")); //LISA
        pandoraBots.add(new PandoraBot("b0dafd24ee35a477", "www")); //CHOMSKI
        pandoraBots.add(new PandoraBot("cf7aa84b0e34555c", "www")); //GLaDOS
        pandoraBots.add(new PandoraBot("b8c97d77ae3471d8", "www")); //LEVI
        pandoraBots.add(new PandoraBot("dbf443e58e345c14", "www")); //FARHA
        pandoraBots.add(new PandoraBot("c9c4b9bf6e345c25", "www")); //ROZA
        pandoraBots.add(new PandoraBot("9efbc6c80e345e65", "www")); //JERVIS
        pandoraBots.add(new PandoraBot("f6d4afd83e34564d", "www")); //LAUREN
        pandoraBots.add(new PandoraBot("a66718a38e345c15", "www")); //LAILA
        pandoraBots.add(new PandoraBot("9f72c4271e347150", "www")); //BARBARA
        pandoraBots.add(new PandoraBot("c406f0d66e345c27", "www")); //MARY
        pandoraBots.add(new PandoraBot("c39a3375ae34d985", "www")); //SANTAS_ROBOT
    }

    private static final Logger log = LoggerFactory.getLogger(ChatbotsAPI.class);


    private ChatbotsAPI() {
    }

    /**
     * @param input the text written by the user
     * @param id    id of the user or guild, defining the session scope
     * @return the answer of a chatbot to the users input
     */
    public static String ask(String input, String id) {
        return ask(input, UUID.nameUUIDFromBytes(id.getBytes()));
    }

    /**
     * @param input the text written by the user
     * @return the answer of a chatbot to the users input, using a fredboat wide session. Use the other public ask()
     * method for a better scope control and higher quality of answers
     */
    public static String ask(String input) {
        return ask(input, UUID.nameUUIDFromBytes("fredboat".getBytes()));
    }

    private static String ask(String input, UUID id) {
        String cleaned = input;
        for (String bl : WORD_BLACKLIST) {
            cleaned = cleaned.replaceAll("(?i)" + bl, "I like you"); //case insensitive replace
        }

        String out = "";

        //try one bot after another if we don't get an answer
        for (PandoraBot bot : pandoraBots) {
            //sessions are cheap, we can afford creating them each time instead of saving them which takes a bigger hit on memory
            ChatterBotSession session = new CustomPandoraSession(bot.botId, bot.subDomain, id);
            try {
                out = session.think(cleaned);
            } catch (Exception e) {
                log.error("Exception when id {} asked chatbot {} this: '{}'", id, bot.botId, input, e);
            }

            //did we get an answer?
            if (out != null && !"".equals(out)) {
                break;
            } else {
                log.warn("Chatbot {} failed to answer {}", bot.botId, cleaned);
            }
        }

        //none of the bots gave us an answer
        if (out == null || "".equals(out)) {
            log.error("No chatbot answered question '{}' by id {}", input, id);
            throw new MessagingException("All chatbot services unavailable, please try again later.");
        }

        out = out.replaceAll("<br>", "\n");

        return out;
    }


    /**
     * Some bots answer on a different URL than regular pandora bots
     * (for example https://kakko.pandorabots.com/... instead of https://www.pandorabots.com/...)
     * This slightly smelly class, mostly a copypasta of the original one
     * (com.google.code.chatterbotapi.Pandorabots.Session)
     * allows to set a pandora subdomain.
     * <p>
     * Also allows to set a custom UUID based on the user talking to the bot instead of random new ones every time
     */
    private static class CustomPandoraSession implements ChatterBotSession {
        private final Map<String, String> vars;
        private final String url;

        public CustomPandoraSession(String botId, String subDomain, UUID uuid) {
            vars = new LinkedHashMap<>();
            vars.put("botid", botId);
            vars.put("custid", uuid.toString());
            this.url = "https://" + subDomain + ".pandorabots.com/pandora/talk-xml";
        }

        @Override
        public ChatterBotThought think(ChatterBotThought thought) throws Exception {
            vars.put("input", thought.getText());
            Class<?> clazzUtil = Class.forName("com.google.code.chatterbotapi.Utils");

            Method methodRequest = clazzUtil.getMethod("request", String.class, Map.class, Map.class, Map.class);
            methodRequest.setAccessible(true);
            String response = (String) methodRequest.invoke(null, url, null, null, vars);

            ChatterBotThought responseThought = new ChatterBotThought();

            Method methodXPathSearch = clazzUtil.getMethod("xPathSearch", String.class, String.class);
            methodXPathSearch.setAccessible(true);
            responseThought.setText((String) methodXPathSearch.invoke(null, response, "//result/that/text()"));

            return responseThought;
        }

        @Override
        public String think(String text) throws Exception {
            ChatterBotThought thought = new ChatterBotThought();
            thought.setText(text);
            return think(thought).getText();
        }
    }

    /**
     * Data wrapper
     */
    private static class PandoraBot {
        String botId;
        String subDomain;

        public PandoraBot(String botId, String subDomain) {
            this.botId = botId;
            this.subDomain = subDomain;
        }
    }
}
