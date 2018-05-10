package uk.gov.cdp.shadow.user.auth.util;
/*
  Author: Deepesh Rathore
 */
import java.util.Properties;

public class PropertiesUtil {

  private static final Properties properties = System.getProperties();

  public static String property(String propertyName) {
    String value = (String) properties.get(propertyName);
    if (value == null) {
      throw new RuntimeException(String.format("Mandatory property {%s} not found ", propertyName));
    } else {
      return value;
    }
  }
}
