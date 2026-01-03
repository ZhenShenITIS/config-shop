package tg.configshop.telegram.commands.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.model.BotUser;
import tg.configshop.services.RegistrationService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.commands.Command;
import tg.configshop.constants.CommandName;
import tg.configshop.constants.MessageText;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


@Component
@RequiredArgsConstructor
public class StartCommand implements Command {
    private final CommandName commandName = CommandName.START;
    private final RegistrationService registrationService;
    private final UserService userService;

    @Value("${CONFIG_PANEL_SUB_URL}")
    private String subUrl;

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    @Override
    public CommandName getCommand() {
        return commandName;
    }

    @Override
    public void handleCommand(Message message, TelegramClient telegramClient) {
        long chatId = message.getChatId();
        User user = message.getFrom();
        long userId = user.getId();
        BotUser botUser;
        if (!registrationService.isRegistered(userId)) {
            botUser = registrationService.registerUser(message);
        } else {
            botUser = userService.getUser(userId);
        }


        String textToSend = isExpired(botUser) ?
                MessageText.START_TEXT_EXPIRED.getMessageText().formatted(user.getFirstName())
                :
                MessageText.START_TEXT_ACTIVE.getMessageText().formatted(user.getFirstName(), getDate(botUser));

        SendMessage sendMessage = SendMessage
                .builder()
                .text(textToSend)
                .chatId(chatId)
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(ButtonText.CONNECT.getText())
                                        .url(subUrl+"/"+botUser.getShortId())
                                        .build()
                        ))
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        // TODO Callback
                                        .text(ButtonText.BALANCE.getText().formatted(botUser.getBalance()))
                                        .callbackData("call")
                                        .build()
                        ))
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        // TODO Callback
                                        .text(ButtonText.SUBSCRIPTION.getText())
                                        .callbackData("call")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        // TODO Callback
                                        .text(ButtonText.PROMO_CODE.getText())
                                        .callbackData("call")
                                        .build()
                        ))
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        // TODO Callback
                                        .text(ButtonText.REFERRAL.getText())
                                        .callbackData("call")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(ButtonText.SUPPORT.getText())
                                        .url("t.me/" + supportUsername)
                                        .build()
                        ))
                        .build())
                .build();

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }


    // TODO Embedding these methods in a util class
    private boolean isExpired (BotUser botUser) {
        return botUser.getExpireAt().isBefore(Instant.now());
    }
    private String getDate (BotUser botUser) {
        Instant date = botUser.getExpireAt();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        ZonedDateTime zdt = date.atZone(ZoneId.of("Europe/Moscow"));
        return zdt.format(formatter);
    }
}
