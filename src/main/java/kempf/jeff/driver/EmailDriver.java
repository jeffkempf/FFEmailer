package kempf.jeff.driver;

import kempf.jeff.services.EmailParser;
import kempf.jeff.services.RefinedEmailParser;
import kempf.jeff.util.PropertiesUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Properties;

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
        String iniPath = properties.getProperty("ini.file");
        String logDir = properties.getProperty("log.dir");

        System.setProperty("log_dir", logDir);
        logger = LogManager.getLogger(EmailDriver.class.getName());


        //email reader app will be single threaded
//        EmailParser ep = new EmailParser(properties);
//        ep.fetch();
        RefinedEmailParser rep = new RefinedEmailParser(properties);
        rep.fetch();
        System.out.println("End of app");


    }
}
