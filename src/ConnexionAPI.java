import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConnexionAPI {

    private String authorizationHeader;
    private String baseUrl;

    public ConnexionAPI(String api_key) {
        this.authorizationHeader = "Bearer " + api_key;
        this.baseUrl = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    }

    public String sendGetRequest(String endpoint, int difficulty) throws IOException {
        String urlString = baseUrl + "/" + endpoint;
        if (endpoint.equals("generate_work")) {
            urlString = baseUrl + "/" + endpoint + "?d=" + difficulty;
        }
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", authorizationHeader);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    public String sendPostRequest(String endpoint, String requestBody) throws IOException {
        String urlString = baseUrl + "/" + endpoint;
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", authorizationHeader);
        connection.setRequestProperty("Content-Type", "application/json;utf-8");
        connection.setDoOutput(true);

        System.out.println("Request Body: " + requestBody);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes("utf-8");
            os.write(input, 0, input.length);
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            InputStream errorStream = connection.getErrorStream();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
            StringBuilder errorResponse = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorResponse.append(errorLine);
            }
            errorReader.close();
            throw new IOException("Server returned HTTP response code: " + responseCode + " for URL: " + urlString + ". Response: " + errorResponse.toString());
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }
}