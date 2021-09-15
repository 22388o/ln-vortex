CREATE TABLE `banned_utxos`
(
    `outpoint`     VARCHAR(254) PRIMARY KEY NOT NULL,
    `banned_until` TIMESTAMP                NOT NULL,
    `reason`       VARCHAR(254)             NOT NULL
);

CREATE TABLE `rounds`
(
    `round_id`   VARCHAR(254) PRIMARY KEY NOT NULL,
    `status`     VARCHAR(254)             NOT NULL,
    `round_time` TIMESTAMP                NOT NULL,
    `fee_rate`   VARCHAR(254)             NOT NULL,
    `amount`     INTEGER                  NOT NULL,
    `profit`     INTEGER
);

CREATE TABLE `alices`
(
    `peer_id`          VARCHAR(254) PRIMARY KEY NOT NULL,
    `round_id`         VARCHAR(254)             NOT NULL,
    `nonce_path`       VARCHAR(254)             NOT NULL,
    `blinded_output`   VARCHAR(254),
    `change_output`    VARCHAR(254),
    `blind_output_sig` VARCHAR(254),
    constraint `fk_roundId` foreign key (`round_id`) references `rounds` (`round_id`) on update NO ACTION on delete NO ACTION
);

CREATE TABLE `registered_inputs`
(
    `outpoint`    VARCHAR(254) PRIMARY KEY NOT NULL,
    `output`      VARCHAR(254)             NOT NULL,
    `input_proof` VARCHAR(254)             NOT NULL,
    `round_id`    VARCHAR(254)             NOT NULL,
    `peer_id`     VARCHAR(254)             NOT NULL,
    constraint `fk_roundId` foreign key (`round_id`) references `rounds` (`round_id`) on update NO ACTION on delete NO ACTION,
    constraint `fk_peerId` foreign key (`peer_id`) references `alices` (`peer_id`) on update NO ACTION on delete NO ACTION
);

CREATE TABLE `registered_inputs`
(
    `output`   VARCHAR(254) PRIMARY KEY NOT NULL,
    `sig`      VARCHAR(254)             NOT NULL,
    `round_id` VARCHAR(254)             NOT NULL,
    constraint `fk_roundId` foreign key (`round_id`) references `rounds` (`round_id`) on update NO ACTION on delete NO ACTION
);