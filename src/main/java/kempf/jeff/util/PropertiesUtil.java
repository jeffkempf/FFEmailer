package kempf.jeff.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesUtil {

    private static String configPath;


    public static String getConfigPath() {
        return configPath;
    }

    public static void setConfigPath(String configPath) {
        PropertiesUtil.configPath = configPath;
    }

    public static Properties readProperties() {
        InputStream input = null;
        Properties prop = new Properties();
        try {

            input = new FileInputStream(configPath);

            // load a properties file
            prop.load(input);

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
//					logger.error(e);
                    e.printStackTrace();
                }
            }
        }
        return prop;
    }
}