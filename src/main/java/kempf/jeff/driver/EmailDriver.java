package kempf.jeff.driver;

import kempf.jeff.services.EmailParser;
import kempf.jeff.services.RefinedEmailParser;
import kempf.jeff.util.PropertiesUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Properties;

/**
 * Description: I have a buddy that's quite vocal about how much he hates hearing about people's fantasy football teams.
 * The entire purpose of this app is to anonymously bombard him with obviously generated fantasy football text messages.
 * For the sake of scope and simplicity, I've confined this app solely to Yahoo fantasy leagues.
 *
 * How it works: A user forwards his/her emails from Yahoo fantasy sports to an email account I've set up, which this app then
 * reads over.  If the email is a valid fantasy email (ie: from Yahoo and fits one of my message categories (trade accepted,
 * waiver failure, etc), it parses out the football content from the email, stores it as metadata along with some randomly
 * generated values, then forms a text message using the metadata and the corresponding template.  The text is sent to him
 * using Nexmo and an SMS proxy.  The proxy is the key to the entire app.  That's what randomizes the sent number.
 * Sending from the same number defeats the purpose, since he'll just block it.
 *
 * Author: Jeff Kempf
 */
public class EmailDriver {
    private static String configPath;
    private static Properties properties;
    private static long sleepTimer;
    private static Logger logger;

    public static void main(String[] args) {
        //get config file
        configPath = args[0];
        PropertiesUtil.setConfigPath(configPath);
        properties = PropertiesUtil.readProperties();
        sleepTimer = Long.parseLong(properties.getProperty("pull.timer"));
        String logDir = properties.getProperty("log.dir");

        System.setProperty("log_dir", logDir);
        logger = LogManager.getLogger(EmailDriver.class.getName());


        //email reader app will be single threaded
//        EmailParser ep = new EmailParser(properties);
//        ep.fetch();
        RefinedEmailParser rep = new RefinedEmailParser(properties);
        rep.fetch();
        logger.info("End of app");


    }
}
