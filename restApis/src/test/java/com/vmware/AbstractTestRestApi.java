package com.vmware;

import com.vmware.utils.ClasspathResource;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.Properties;

/**
 * Base class for testing rest api.
 * Unit tests assume that you already have a valid cookie / api token stored for the relevant api.
 * Run workflow AuthenticateAllApis to verify
 */
public class AbstractTestRestApi {

    protected static Properties testProperties;

    @BeforeClass
    public static void initProperties() throws IOException, IllegalAccessException {
        testProperties = new Properties();
        testProperties.load(new ClasspathResource("/test.properties").getReader());
    }
}
