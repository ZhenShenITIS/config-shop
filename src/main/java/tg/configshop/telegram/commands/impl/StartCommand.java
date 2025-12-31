package tg.configshop.telegram.commands.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.commands.Command;
import tg.configshop.telegram.constants.CallbackName;
import tg.configshop.telegram.constants.CommandName;
import tg.configshop.telegram.constants.MessageText;


@Component
public class StartCommand implements Command {
    CommandName commandName = CommandName.START;
    @Override
    public CommandName getCommand() {
        return commandName;
    }

    @Override
    public void handleCommand(Message message, TelegramClient telegramClient) {
        long chatId = message.getChatId();
        String textToSend = MessageText.START_TEXT.getMessageText();
        User user = message.getFrom();
        long userId = user.getId();

        SendMessage sendMessage = SendMessage
                .builder()
                .text(textToSend)
                .chatId(chatId)
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(MessageText.PLACEMENTS.getMessageText())
                                        .callbackData(CallbackName.PLACEMENTS.getCallbackName()+":"+userId)
                                        .build()))
                        .keyboardRow( new InlineKeyboardRow
                                (
                                        InlineKeyboardButton
                                                .builder()
                                                .text(MessageText.PERMUTATIONS.getMessageText())
                                                .callbackData(CallbackName.PERMUTATIONS.getCallbackName()+":"+userId)
                                                .build()))
                        .keyboardRow(new InlineKeyboardRow
                                (
                                        InlineKeyboardButton
                                                .builder()
                                                .text(MessageText.COMBINATIONS.getMessageText())
                                                .callbackData(CallbackName.COMBINATIONS.getCallbackName()+":"+userId)
                                                .build()))
                        .keyboardRow(new InlineKeyboardRow
                                (
                                        InlineKeyboardButton
                                                .builder()
                                                .text(MessageText.URN_MODEL.getMessageText())
                                                .callbackData(CallbackName.URN_MODEL.getCallbackName()+":"+userId)
                                                .build()
                                                )
                                                )
                        .build())
                .build();

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
