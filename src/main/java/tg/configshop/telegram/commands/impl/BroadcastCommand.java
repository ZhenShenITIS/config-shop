package tg.configshop.telegram.commands.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CommandName;
import tg.configshop.constants.MessageText;
import tg.configshop.model.BotUser;
import tg.configshop.services.UserService;
import tg.configshop.telegram.commands.Command;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastCommand implements Command {
    private final UserService userService;
    private final Semaphore rateLimiter = new Semaphore(25);

    @Override
    public CommandName getCommand() {
        return CommandName.BROADCAST;
    }

    @Override
    @AdminOnly
    public void handleCommand(Message message, TelegramClient telegramClient) {
        String fullText = message.hasText() ? message.getText() : message.getCaption();

        if (fullText == null) {
            sendError(message.getChatId(), MessageText.BROADCAST_TEXT_NOT_FOUND.getMessageText(), telegramClient);
            return;
        }

        String htmlText = extractTextAfterCommand(fullText);

        if (htmlText.isEmpty()) {
            String errorMsg = String.format(MessageText.BROADCAST_USAGE.getMessageText(),
                    CommandName.BROADCAST.getCommandName());
            sendError(message.getChatId(), errorMsg, telegramClient);
            return;
        }

        String photoFileId = null;
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            photoFileId = photos.getLast().getFileId();
        }

        List<BotUser> allUsers = userService.getAllUsers();

        if (allUsers.isEmpty()) {
            sendError(message.getChatId(), MessageText.BROADCAST_NO_USERS.getMessageText(), telegramClient);
            return;
        }

        sendConfirmation(message.getChatId(),
                String.format(MessageText.BROADCAST_START.getMessageText(), allUsers.size()),
                telegramClient);


        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);

        final String finalPhotoFileId = photoFileId;


        for (BotUser user : allUsers) {
            Thread.startVirtualThread(() -> {
                try {
                    rateLimiter.acquire();

                    if (finalPhotoFileId != null) {
                        sendPhotoWithRetry(user.getId(), htmlText, finalPhotoFileId, telegramClient);
                    } else {
                        sendMessageWithRetry(user.getId(), htmlText, telegramClient);
                    }

                    sent.incrementAndGet();

                } catch (TelegramApiRequestException e) {
                    if (e.getErrorCode() == 403) {
                        blocked.incrementAndGet();
                        log.debug("User {} blocked the bot", user.getId());
                    } else {
                        failed.incrementAndGet();
                        log.error("Failed to send to user {}: {}", user.getId(), e.getMessage());
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Unexpected error for user {}: {}", user.getId(), e.getMessage());
                } finally {
                    rateLimiter.release();
                }

                int total = sent.get() + failed.get() + blocked.get();
                if (total % 100 == 0) {
                    log.info("Broadcast Progress: {}/{}", total, allUsers.size());
                }
            });

            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }


        Thread.startVirtualThread(() -> {
            try {

                Thread.sleep(15000);
                String report = String.format(
                        MessageText.BROADCAST_REPORT.getMessageText(),
                        sent.get(), failed.get(), blocked.get()
                );
                sendConfirmation(message.getChatId(), report, telegramClient);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }



    private void sendMessageWithRetry(Long chatId, String htmlText, TelegramClient telegramClient)
            throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(htmlText)
                .parseMode("HTML")
                .build();

        executeWithRetry(() -> telegramClient.execute(sendMessage));
    }

    private void sendPhotoWithRetry(Long chatId, String caption, String photoFileId,
                                    TelegramClient telegramClient) throws TelegramApiException {
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(photoFileId))
                .caption(caption)
                .parseMode("HTML")
                .build();

        executeWithRetry(() -> telegramClient.execute(sendPhoto));
    }

    private void executeWithRetry(ThrowingSupplier action) throws TelegramApiException {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                action.execute();
                return;
            } catch (TelegramApiRequestException e) {
                if (e.getErrorCode() == 429) {
                    Integer retryAfter = extractRetryAfter(e);
                    log.warn("Rate limit hit, waiting {} seconds...", retryAfter);

                    try {
                        Thread.sleep(retryAfter * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    attempt++;
                } else {
                    throw e;
                }
            }
        }
        throw new TelegramApiException("Failed after " + maxRetries + " retries");
    }

    private Integer extractRetryAfter(TelegramApiRequestException e) {
        if (e.getParameters() != null && e.getParameters().getRetryAfter() != null) {
            return e.getParameters().getRetryAfter();
        }
        Pattern pattern = Pattern.compile("retry after (\\d+)");
        Matcher matcher = pattern.matcher(e.getMessage().toLowerCase());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 5;
    }

    private String extractTextAfterCommand(String messageText) {
        String[] parts = messageText.split(" ", 2);
        return parts.length > 1 ? parts[1].trim() : "";
    }

    private void sendError(Long chatId, String text, TelegramClient telegramClient) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }

    private void sendConfirmation(Long chatId, String text, TelegramClient telegramClient) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to send confirmation: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        void execute() throws TelegramApiException;
    }
}
