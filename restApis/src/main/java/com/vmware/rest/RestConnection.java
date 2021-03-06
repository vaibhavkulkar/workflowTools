package com.vmware.rest;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.vmware.rest.cookie.ApiAuthentication;
import com.vmware.rest.cookie.Cookie;
import com.vmware.rest.cookie.CookieFileStore;
import com.vmware.rest.credentials.UsernamePasswordCredentials;
import com.vmware.rest.exception.ExceptionChecker;
import com.vmware.rest.json.ConfiguredGsonBuilder;
import com.vmware.rest.request.OverwritableSet;
import com.vmware.rest.request.RequestBodyFactory;
import com.vmware.rest.request.RequestBodyHandling;
import com.vmware.rest.request.RequestHeader;
import com.vmware.rest.request.RequestParam;
import com.vmware.utils.IOUtils;
import com.vmware.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.vmware.rest.HttpMethodType.GET;
import static com.vmware.rest.HttpMethodType.POST;
import static com.vmware.rest.HttpMethodType.PUT;
import static com.vmware.rest.HttpMethodType.DELETE;
import static com.vmware.rest.request.RequestHeader.anAcceptHeader;

/**
 * Using Java's HttpURLConnection instead of Apache HttpClient to cut down on jar size
 */
public class RestConnection {

    private static Logger log = LoggerFactory.getLogger(RestConnection.class.getName());
    private static int CONNECTION_TIMEOUT = (int) TimeUnit.MILLISECONDS.convert(25, TimeUnit.SECONDS);
    private static final int MAX_REQUEST_RETRIES = 3;

    private final CookieFileStore cookieFileStore;
    private Gson gson;
    private RequestBodyHandling requestBodyHandling;
    private Set<RequestParam> statefulParams = new OverwritableSet<RequestParam>();
    private HttpURLConnection activeConnection;
    private boolean useSessionCookies;

    public RestConnection(RequestBodyHandling requestBodyHandling) throws IOException {
        this.requestBodyHandling = requestBodyHandling;
        this.gson = new ConfiguredGsonBuilder().build();

        String homeFolder = System.getProperty("user.home");
        cookieFileStore = new CookieFileStore(homeFolder);
    }

    public void updateServerTimeZone(TimeZone serverTimezone, String serverDateFormat) {
        this.gson = new ConfiguredGsonBuilder(serverTimezone, serverDateFormat).build();
    }

    public void setupBasicAuthHeader(final UsernamePasswordCredentials credentials) {
        String basicCredentials = DatatypeConverter.printBase64Binary(credentials.toString().getBytes());
        RequestHeader authorizationHeader = new RequestHeader("Authorization", "Basic " + basicCredentials);
        statefulParams.add(authorizationHeader);
    }

    public void addStatefulParams(List<? extends RequestParam> params) {
        statefulParams.addAll(params);
    }

    public void clearStatefulParams() {
        statefulParams.clear();
    }

    public <T> T get(String url, Class<T> responseConversionClass, List<RequestParam> params) throws IOException, URISyntaxException {
        return get(url, responseConversionClass, params.toArray(new RequestParam[params.size()]));
    }

    public <T> T get(String url, Class<T> responseConversionClass, RequestParam... params)
            throws IOException, URISyntaxException {
        setupConnection(url, GET, params);
        return handleServerResponse(responseConversionClass);
    }

    public <T> T put(String url, Class<T> responseConversionClass, Object requestObject, RequestParam... params)
            throws URISyntaxException, IOException, IllegalAccessException {
        setupConnection(url, PUT, params);
        RequestBodyFactory.setRequestDataForConnection(this, requestObject);
        return handleServerResponse(responseConversionClass);
    }

    public <T> T post(String url, Class<T> responseConversionClass, Object requestObject, RequestParam... params)
            throws URISyntaxException, IOException, IllegalAccessException {
        setupConnection(url, POST, params);
        RequestBodyFactory.setRequestDataForConnection(this, requestObject);
        return handleServerResponse(responseConversionClass);
    }

    public <T> T put(String url, Object requestObject, RequestParam... params)
            throws URISyntaxException, IOException, IllegalAccessException {
        return put(url, null, requestObject, params);
    }

    public <T> T post(String url, Object requestObject, RequestParam... params)
            throws URISyntaxException, IOException, IllegalAccessException {
        return post(url, null, requestObject, params);
    }

    public <T> T delete(String url, RequestParam... params)
            throws URISyntaxException, IOException, IllegalAccessException {
        setupConnection(url, DELETE, params);
        return handleServerResponse(null);
    }

    public void setRequestBodyHandling(final RequestBodyHandling requestBodyHandling) {
        this.requestBodyHandling = requestBodyHandling;
    }

    public boolean hasCookie(ApiAuthentication ApiAuthentication) {
        Cookie cookie = cookieFileStore.getCookieByName(ApiAuthentication.getCookieName());
        return cookie != null;
    }

    public void setRequestProperty(String name, String value) {
        activeConnection.setRequestProperty(name, value);
    }

    public OutputStream getOutputStream() throws IOException {
        return activeConnection.getOutputStream();
    }

    public RequestBodyHandling getRequestBodyHandling() {
        return requestBodyHandling;
    }

    public void setDoOutput(boolean value) {
        activeConnection.setDoOutput(value);
    }

    public void setUseSessionCookies(boolean useSessionCookies) {
        this.useSessionCookies = useSessionCookies;
    }

    public String toJson(Object value) {
        return gson.toJson(value);
    }

    private void setupConnection(String requestUrl, HttpMethodType methodType, RequestParam... statelessParams) throws IOException, URISyntaxException {
        Set<RequestParam> allParams = new OverwritableSet<RequestParam>(statefulParams);
        // add default application json header, can be overridden by stateless headers
        allParams.add(anAcceptHeader("application/json"));

        List<RequestParam> statelessParamsList = Arrays.asList(statelessParams);
        allParams.addAll(statelessParamsList);
        String fullUrl = UrlUtils.buildUrl(requestUrl, allParams);
        URI uri = new URI(fullUrl);
        log.debug("{}: {}", methodType.name(), uri.toString());

        activeConnection = (HttpURLConnection) uri.toURL().openConnection();
        activeConnection.setDoInput(true);
        activeConnection.setConnectTimeout(CONNECTION_TIMEOUT);
        activeConnection.setReadTimeout(CONNECTION_TIMEOUT);
        activeConnection.setInstanceFollowRedirects(false);
        activeConnection.setRequestMethod(methodType.name());
        addRequestHeaders(allParams);
        addCookiesHeader(uri.getHost());
    }

    private void addRequestHeaders(Collection<? extends RequestParam> params) {
        for (RequestParam param : params) {
            if (!(param instanceof RequestHeader)) {
                continue;
            }
            RequestHeader header = (RequestHeader) param;
            log.debug("Adding request header {}:{}", header.getName(), header.getValue());
            activeConnection.setRequestProperty(header.getName(), header.getValue());
        }
    }

    private <T> T handleServerResponse(final Class<T> responseConversionClass) throws IOException {
        String responseText = getResponseText(0);
        activeConnection.disconnect();
        if (responseText.isEmpty() || responseConversionClass == null) {
            return null;
        } else {
            try {
                return gson.fromJson(responseText, responseConversionClass);
            } catch (JsonSyntaxException e) {
                // allow a parsing attempt as it could be a json string primitive
                if (responseConversionClass.equals(String.class)) {
                    return (T) responseText;
                } else {
                    log.error("Failed to parse response text\n{}", responseText);
                    throw e;
                }
            }
        }
    }

    private void addCookiesHeader(String host) {
        activeConnection.setRequestProperty("Cookie", cookieFileStore.toCookieRequestText(host, useSessionCookies));
    }

    private String getResponseText(int retryCount) throws IOException {
        String responseText = "";
        try {
            responseText = parseResponseText();
            cookieFileStore.addCookiesFromResponse(activeConnection);
        } catch (SSLException e) {
            exitIfMaxRetriesReached(retryCount, e);

            ThreadUtils.sleep(TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS));
            log.info("");
            log.info("Retrying request {} of {}", ++retryCount, MAX_REQUEST_RETRIES);
            responseText = getResponseText(retryCount);
        } catch (UnknownHostException e) {
            handleNetworkException(e);
        } catch (SocketException e) {
            handleNetworkException(e);
        }
        return responseText;
    }

    private void exitIfMaxRetriesReached(int retryCount, SSLException e) {
        String urlText = activeConnection.getURL().toString();
        log.error("Ssl error for {} {}", activeConnection.getRequestMethod(), urlText);
        log.error("Error [{}]" ,e.getMessage());
        if (retryCount >= MAX_REQUEST_RETRIES) {
            exitDueToSslExceptions(urlText);
        }
    }

    private void exitDueToSslExceptions(String urlText) {
        log.info("");
        log.info("Still getting ssl errors, can't proceed");
        if (urlText.contains("reviewboard")) {
            log.info("Sometimes there are one off ssl exceptions with the reviewboard api, try rerunning your workflow");
        }
        System.exit(1);
    }

    private void handleNetworkException(IOException e) {
        log.info("");
        log.error("Unknown host exception thrown: {}", e.getMessage());
        log.error("Are you connected to the corporate network?");
        log.error("Failed to access host {}", activeConnection.getURL().getHost());
        System.exit(1);
    }

    private String parseResponseText() throws IOException {
        String responseText;
        String currentUrl = activeConnection.getURL().toExternalForm();
        int responseCode = activeConnection.getResponseCode();
        if (ExceptionChecker.isStatusValid(responseCode)) {
            responseText = IOUtils.read(activeConnection.getInputStream());
        } else {
            responseText = IOUtils.read(activeConnection.getErrorStream());
        }
        ExceptionChecker.throwExceptionIfStatusIsNotValid(currentUrl, responseCode, responseText);
        return responseText;
    }

}
