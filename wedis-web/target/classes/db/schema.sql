--DROP table `connection_info`;
CREATE TABLE IF NOT EXISTS `connection_info` (
	`id` int(11) NOT NULL AUTO_INCREMENT,
	`name` VARCHAR(100) NOT NULL UNIQUE,
	`host` VARCHAR(100) NOT NULL,
	`port` int(8) NOT NULL DEFAULT 6379,
	`pwd` VARCHAR(100),
	PRIMARY KEY (`id`)
);