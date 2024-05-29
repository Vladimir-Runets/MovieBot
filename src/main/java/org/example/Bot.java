package org.example;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bot extends TelegramLongPollingBot {

    static boolean isMovieSearch, isGenreSearch;

    public Bot() {}

    // Метод для получения имени бота
    @Override
    public String getBotUsername() {
        return "...";
    }

    // Метод для получения токена бота
    @Override
    public String getBotToken() {
        return "...";
    }

    // Метод для отправки текстового сообщения пользователю
    public void sendText(Long who, String what){
        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        // Создаем строки клавиатуры
        ArrayList<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow searchRow = new KeyboardRow();
        searchRow.add("Поиск фильма");
        KeyboardRow genreRow = new KeyboardRow();
        genreRow.add("Подборка фильмов по жанрам");
        KeyboardRow endRow = new KeyboardRow();
        endRow.add("Завершить");

        // Добавляем строки в клавиатуру
        keyboard.add(searchRow);
        keyboard.add(genreRow);
        keyboard.add(endRow);

        // Устанавливаем клавиатуру в сообщение
        keyboardMarkup.setKeyboard(keyboard);

        SendMessage sm = new SendMessage();
        sm.setChatId(who.toString());
        sm.setText(what);
        sm.setReplyMarkup(keyboardMarkup);
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    // Метод, который вызывается при получении обновления (сообщения) от пользователя
    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        String text = message.getText();
        long userId = message.getFrom().getId();

        // Обработка сообщений от пользователя
        if (text.equals("/start")) {
            sendText(userId, "Приветствую, " + message.getFrom().getFirstName() + "! \uD83C\uDFAC\uD83C\uDF7F Я здесь, чтобы помочь тебе найти идеальный фильм! Выбери, что ты хочешь сделать:\n\n" +
                    "\t\t\tПоиск фильма \uD83D\uDD0D \n\n" +
                    "\t\t\tПодборка фильмов по жанрам \uD83C\uDFA5 \n\n" +
                    "Если захочешь завершить наше взаимодействие, нажми Завершить");
        } else if (text.equals("Поиск фильма")) {
            isMovieSearch = true;
            isGenreSearch = false;
            sendText(userId, "Отправь мне название фильма, и я найду его для тебя!");
        } else if (text.equals("Подборка фильмов по жанрам")) {
            isGenreSearch = true;
            isMovieSearch = false;
            sendText(userId, "Отправь мне свои любимые жанры, и я пришлю тебе три лучших фильма по этим жанрам!\n\nПример: Драма, Фэнтези, Комедия");
        } else if (text.equals("Завершить")) {
            sendText(userId, "Спасибо за использование моих услуг! До свидания!");
            System.exit(0);
        } else {
            if(isMovieSearch){
                searchMovieByTitle(text, userId);
            } else if (isGenreSearch) {
                // Разбиваем текст на слова по запятым или пробелам и создаем из них список
                String[] splitWords = text.split("\\s+|,");
                ArrayList<String> genres = new ArrayList<>();
                for (String word : splitWords) {
                    if (!word.isEmpty()) {
                        genres.add(word.trim());
                    }
                }

                // Вызываем функцию recommendMoviesByGenres, передавая список жанров
                recommendMoviesByGenres(userId, genres);
            } else {
                sendText(userId, "Неверная команда =(. Попробуй ещё раз");
            }
        }
    }

    public void searchMovieByTitle(String title, long userId) {
        String responseBody = "", year = "", filmLength = "", countriesText, genresText, posterUrl = "";
        String movieTitle = title.replaceAll(" ", "+");
        String url = "https://www.kinopoisk.ru/index.php?first=yes&kp_query=" + movieTitle;
        String apiKey = "...";
        String apiUrl = "https://kinopoiskapiunofficial.tech/api/v2.1/films/search-by-keyword?keyword=" + movieTitle;
        StringBuilder countries = new StringBuilder();
        StringBuilder genres = new StringBuilder();

        HttpClient httpClient = HttpClientBuilder.create().build();

        try {
            // Создание GET-запроса к API kinopoisk.ru
            HttpGet httpGet = new HttpGet(apiUrl);
            httpGet.addHeader("X-API-KEY", apiKey);
            httpGet.addHeader("Content-Type", "application/json");

            // Выполнение запроса
            HttpResponse response = httpClient.execute(httpGet);

            // Получение ответа в виде HTTP-сущности
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseBody = EntityUtils.toString(entity);
            }

            // Регулярные выражения для извлечения информации о фильме
            Pattern yearPattern = Pattern.compile("\"year\":\"(\\d+)\"");
            Pattern filmLengthPattern = Pattern.compile("\"filmLength\":\"(\\d+:\\d+)\"");
            Pattern countriesPattern = Pattern.compile("\"countries\":\\s*\\[([^\\]]*)\\]");
            Pattern citiesPattern = Pattern.compile("\"country\":\"(.*?)\"");
            Pattern genresPattern = Pattern.compile("\"genres\":\\s*\\[([^\\]]*)\\]");
            Pattern genrePattern = Pattern.compile("\"genre\":\"(.*?)\"");
            Pattern posterUrlPattern = Pattern.compile("\"posterUrl\":\"(.*?)\"");

            Matcher yearMatcher = yearPattern.matcher(responseBody);
            Matcher filmLengthMatcher = filmLengthPattern.matcher(responseBody);
            Matcher countriesMatcher = countriesPattern.matcher(responseBody);
            Matcher genresMatcher = genresPattern.matcher(responseBody);
            Matcher posterUrlMatcher = posterUrlPattern.matcher(responseBody);

            // Поиск соответствий и извлечение данных
            if (yearMatcher.find()) {
                year = yearMatcher.group(1);
            }

            if (filmLengthMatcher.find()) {
                String[] filmLengthParts = filmLengthMatcher.group(1).split(":");
                int hours = Integer.parseInt(filmLengthParts[0]);
                int minutes = Integer.parseInt(filmLengthParts[1]);
                int totalMinutes = hours * 60 + minutes;
                filmLength = totalMinutes + " мин";
            }

            if (countriesMatcher.find()) {
                countriesText = countriesMatcher.group(1);
                Matcher citiesMatcher = citiesPattern.matcher(countriesText);
                while (citiesMatcher.find()){
                    countries.append(citiesMatcher.group(1)).append(", ");
                }
                if (countries.length() > 0) {
                    countries.setLength(countries.length() - 2);
                }
            }

            if (genresMatcher.find()) {
                genresText = genresMatcher.group(1);
                Matcher genreMatcher = genrePattern.matcher(genresText);
                while (genreMatcher.find()){
                    String genre = genreMatcher.group(1);
                    genre = genre.substring(0, 1).toUpperCase() + genre.substring(1);
                    genres.append(genre).append(", ");
                }
                if (genres.length() > 0) {
                    genres.setLength(genres.length() - 2);
                }
            }

            if (posterUrlMatcher.find()) {
                posterUrl = posterUrlMatcher.group(1);
            }

            URL imageUrl = new URL(posterUrl);
            InputFile photo = new InputFile(imageUrl.openStream(), "photo.png");
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(String.valueOf(userId));
            sendPhoto.setPhoto(photo);
            execute(sendPhoto);
            sendText(userId, "Год выпуска: " + year + "\n" +
                    "Страна: " + countries + "\n" +
                    "Жанр: " + genres + "\n" +
                    "Продолжительность: " + filmLength + ".");
            sendText(userId, "Ссылка на просмотр фильма: " + url);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void recommendMoviesByGenres(long userId, ArrayList<String> favoriteGenres) {
        StringBuilder filmRecommendations = new StringBuilder();

        try {
            String apiKey = "...";
            String request = "Скажи мне 3 лучших фильма следующих жанров: " + favoriteGenres + ". Ответ необходимо предоставить в следующем формате: название фильма в кавычках(год выхода) - Краткое описание фильма. (абзац ---------- абзац)";
            // URL для отправки запроса
            URL url = new URL("https://api.proxyapi.ru/openai/v1/chat/completions");

            // Открытие соединения
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");

            // Установка заголовков запроса
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            // Включение отправки данных в тело запроса
            connection.setDoOutput(true);

            // JSON-тело запроса
            String requestBody = "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": \"" + request + "\"}], \"temperature\": 0.7}";

            // Отправка данных в тело запроса
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Чтение ответа
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                // Регулярное выражение для поиска всего текста до первой закрывающей фигурной скобки
                String regex = "\"content\": \"([^\"]*.*?)\"}";

                // Компилируем регулярное выражение
                Pattern pattern = Pattern.compile(regex);

                // Создаем Matcher объект
                Matcher matcher = pattern.matcher(response);

                // Ищем совпадения
                if (matcher.find()) {
                    // Выводим найденный текст до закрывающей фигурной скобки
                    filmRecommendations = new StringBuilder(matcher.group(1).replace("\\n", "\n").replace("\\", ""));;
                }
            }

            // Закрытие соединения
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        filmRecommendations.append("\n\n");

        // Создаем шаблон регулярного выражения для поиска названий фильмов в кавычках
        Pattern pattern = Pattern.compile("\"([^\"]*)\"");

        // Создаем объект Matcher для поиска совпадений в строке
        Matcher matcher = pattern.matcher(filmRecommendations.toString());

        // Находим и выводим названия фильмов без кавычек
        while (matcher.find()) {
            String movieTitle = matcher.group(1);
            filmRecommendations.append("Ссылка на просмотр фильма \"").append(movieTitle).append("\": https://www.kinopoisk.ru/index.php?first=yes&kp_query=").append(movieTitle.replaceAll(" ", "+")).append("\n");
        }
        sendText(userId, filmRecommendations.toString());
    }
}