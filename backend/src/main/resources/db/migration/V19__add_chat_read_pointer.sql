ALTER TABLE chat_room_participations ADD COLUMN last_read_message_id BIGINT;

ALTER TABLE chat_room_participations
    ADD CONSTRAINT fk_chat_room_participations_last_read_message_id
    FOREIGN KEY (last_read_message_id) REFERENCES chats (id);

ALTER TABLE chats DROP COLUMN is_read;
