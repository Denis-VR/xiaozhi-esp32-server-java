-- Add the following statements at the top of the file
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Create local user and set password (using mysql_native_password plugin)
CREATE USER IF NOT EXISTS 'xiaozhi'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456';

-- Create remote user and set password (using mysql_native_password plugin)
CREATE USER IF NOT EXISTS 'xiaozhi'@'%' IDENTIFIED WITH mysql_native_password BY '123456';

-- Grant all privileges on xiaozhi database to local user only
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';

-- Grant all privileges on xiaozhi database to remote user only
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'%';

-- Flush privileges to apply changes
FLUSH PRIVILEGES;

-- View user grants
SHOW GRANTS FOR 'xiaozhi'@'localhost';
SHOW GRANTS FOR 'xiaozhi'@'%';

-- Create database (if not exists)
CREATE DATABASE IF NOT EXISTS `xiaozhi` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- xiaozhi.sys_user definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_user`;
CREATE TABLE `xiaozhi`.`sys_user` (
  `userId` int unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `wxOpenId` VARCHAR(100) NULL COMMENT 'WeChat OpenId',
  `wxUnionId` VARCHAR(100) NULL COMMENT 'WeChat UnionId',
  `tel` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `roleId` int unsigned NOT NULL DEFAULT 2 COMMENT 'Role ID',
  `avatar` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Avatar',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '1-active 0-disabled',
  `loginIp` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `isAdmin` enum('1','0') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `loginTime` datetime DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`userId`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert admin user only if it does not exist
INSERT INTO xiaozhi.sys_user (username, password, state, isAdmin, roleId, name, createTime, updateTime)
VALUES ('admin', '11cd9c061d614dcf37ec60c44c11d2ad', '1', '1', 1, 'Xiaozhi', '2025-03-09 18:32:29', '2025-03-09 18:32:35');

update `xiaozhi`.`sys_user` set name = 'Xiaozhi' where username = 'admin';

-- xiaozhi.sys_device definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_device`;
CREATE TABLE `xiaozhi`.`sys_device` (
  `deviceId` varchar(255) NOT NULL COMMENT 'Device ID, primary key',
  `deviceName` varchar(100) NOT NULL COMMENT 'Device name',
  `roleId` int unsigned DEFAULT NULL COMMENT 'Role ID, primary key',
  `function_names` varchar(250) NULL COMMENT 'List of available global function names (comma separated); empty to use all',
  `ip` varchar(45) DEFAULT NULL COMMENT 'IP address',
  `location` varchar(255) DEFAULT NULL COMMENT 'Geolocation',
  `wifiName` varchar(100) DEFAULT NULL COMMENT 'WiFi name',
  `chipModelName` varchar(100) DEFAULT NULL COMMENT 'Chip model',
  `type` varchar(50) DEFAULT NULL COMMENT 'Device type',
  `version` varchar(50) DEFAULT NULL COMMENT 'Firmware version',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT 'Device status: 1-online, 0-offline',
  `userId` int NOT NULL COMMENT 'Creator',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `lastLogin` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last login time',
  PRIMARY KEY (`deviceId`),
  KEY `deviceName` (`deviceName`),
  KEY `userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Device information table';

-- xiaozhi.sys_message definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_message`;
CREATE TABLE `xiaozhi`.`sys_message` (
  `messageId` bigint NOT NULL AUTO_INCREMENT COMMENT 'Message ID, primary key, auto increment',
  `deviceId` varchar(30) NOT NULL COMMENT 'Device ID',
  `sessionId` varchar(100) NOT NULL COMMENT 'Session ID',
  `sender` enum('user','assistant') NOT NULL COMMENT 'Sender: user or assistant',
  `roleId` bigint COMMENT 'Role ID played by AI',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Message content',
  `messageType` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Message type',
  `audioPath` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Audio file path',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT 'State: 1-valid, 0-deleted',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Message send time',
  PRIMARY KEY (`messageId`),
  KEY `deviceId` (`deviceId`),
  KEY `sessionId` (`sessionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Human-AI conversation messages table';

-- xiaozhi.sys_role definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_role`;
CREATE TABLE `xiaozhi`.`sys_role` (
  `roleId` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Role ID, primary key',
  `roleName` varchar(100) NOT NULL COMMENT 'Role name',
  `roleDesc` TEXT DEFAULT NULL COMMENT 'Role description',
  `avatar` varchar(255) DEFAULT NULL COMMENT 'Role avatar',
  `ttsId` int DEFAULT NULL COMMENT 'TTS service ID',
  `modelId` int unsigned DEFAULT NULL COMMENT 'Model ID',
  `sttId` int unsigned DEFAULT NULL COMMENT 'STT service ID',
  `vadSpeechTh` FLOAT DEFAULT 0.5 COMMENT 'VAD speech threshold',
  `vadSilenceTh` FLOAT DEFAULT 0.3 COMMENT 'VAD silence threshold',
  `vadEnergyTh` FLOAT DEFAULT 0.01 COMMENT 'VAD energy threshold',
  `vadSilenceMs` INT DEFAULT 1200 COMMENT 'VAD silence time (ms)',
  `voiceName` varchar(100) NOT NULL COMMENT 'Role voice name',
  `ttsPitch` FLOAT DEFAULT 1.0 COMMENT 'TTS pitch',
  `ttsSpeed` FLOAT DEFAULT 1.0 COMMENT 'TTS speed',
  `state` enum('1','0') DEFAULT '1' COMMENT 'State: 1-enabled, 0-disabled',
  `isDefault` enum('1','0') DEFAULT '0' COMMENT 'Default role: 1-yes, 0-no',
  `userId` int NOT NULL COMMENT 'Creator',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`roleId`),
  KEY `userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role table';

-- xiaozhi.sys_code definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_code`;
CREATE TABLE `xiaozhi`.`sys_code` (
  `codeId` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `code` varchar(100) NOT NULL COMMENT 'Verification code',
  `type` varchar(50) DEFAULT NULL COMMENT 'Device type',
  `email` varchar(100) DEFAULT NULL COMMENT 'Email',
  `deviceId` varchar(30) DEFAULT NULL COMMENT 'Device ID',
  `sessionId` varchar(100) DEFAULT NULL COMMENT 'Session ID',
  `audioPath` text COMMENT 'Audio file path',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`codeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Verification code table';

-- xiaozhi.sys_config definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_config`;
CREATE TABLE `xiaozhi`.`sys_config` (
  `configId` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Config ID, primary key',
  `userId` int NOT NULL COMMENT 'Creator user ID',
  `configType` varchar(30) NOT NULL COMMENT 'Config type (llm, stt, tts, etc)',
  `modelType` varchar(30) DEFAULT NULL COMMENT 'LLM model type (chat, vision, intent, embedding, etc)',
  `provider` varchar(30) NOT NULL COMMENT 'Provider (openai, vosk, aliyun, tencent, etc)',
  `configName` varchar(50) DEFAULT NULL COMMENT 'Config name',
  `configDesc` TEXT DEFAULT NULL COMMENT 'Config description',
  `appId` varchar(100) DEFAULT NULL COMMENT 'APP ID',
  `apiKey` varchar(255) DEFAULT NULL COMMENT 'API key',
  `apiSecret` varchar(255) DEFAULT NULL COMMENT 'API secret',
  `ak` varchar(255) DEFAULT NULL COMMENT 'Access Key',
  `sk` text DEFAULT NULL COMMENT 'Secret Key',
  `apiUrl` varchar(255) DEFAULT NULL COMMENT 'API URL',
  `isDefault` enum('1','0') DEFAULT '0' COMMENT 'Default config: 1-yes, 0-no',
  `state` enum('1','0') DEFAULT '1' COMMENT 'State: 1-enabled, 0-disabled',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`configId`),
  KEY `userId` (`userId`),
  KEY `configType` (`configType`),
  KEY `provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System configuration table (models, speech recognition, speech synthesis, etc)';

-- xiaozhi.sys_template definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_template`;
CREATE TABLE `xiaozhi`.`sys_template` (
  `userId` int NOT NULL COMMENT 'Creator user ID',
  `templateId` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Template ID',
  `templateName` varchar(100) NOT NULL COMMENT 'Template name',
  `templateDesc` varchar(500) DEFAULT NULL COMMENT 'Template description',
  `templateContent` text NOT NULL COMMENT 'Template content',
  `category` varchar(50) DEFAULT NULL COMMENT 'Template category',
  `isDefault` enum('1','0') DEFAULT '0' COMMENT 'Default config: 1-yes, 0-no',
  `state` enum('1','0') DEFAULT '1' COMMENT 'State (1 enabled, 0 disabled)',
  `createTime` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updateTime` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`templateId`),
  KEY `category` (`category`),
  KEY `templateName` (`templateName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt template table';

-- Insert default template
INSERT INTO `xiaozhi`.`sys_template` (`userId`, `templateName`, `templateDesc`, `templateContent`, `category`, `isDefault`) VALUES
(1, 'General Assistant', 'A general AI assistant suitable for daily conversations', 'You are a helpful AI assistant. Answer user questions in a friendly and professional way. Provide accurate and useful information, and keep answers as concise as possible. Avoid complex symbols or formatting; maintain a natural and smooth conversational style. When the user''s question is unclear, politely ask for more information. Remember that your answers will be converted to speech, so use clear, easy-to-read language.', 'Basic Role', '0'),

(1, 'Educator', 'A teacher role skilled at explaining complex concepts', 'You are an experienced teacher who explains complex concepts in simple, understandable ways. When answering, consider learners at different levels, use appropriate analogies and examples, and encourage critical thinking. Avoid symbols or formulas that are difficult to express in speech; use clear language to describe concepts. Guide the learning process rather than giving direct answers. Use a natural tone and rhythm, as if explaining in a classroom.', 'Professional Role', '0'),

(1, 'Domain Expert', 'An expert role that provides in-depth professional knowledge', 'You are an expert in a specific field with deep professional knowledge. Provide thorough and accurate information when answering. You may mention relevant studies or data, but avoid overly complex citation formats. Use appropriate technical terms while ensuring explanations of complex concepts so non-experts can understand. Avoid charts or tables that cannot be expressed in speech; use clear descriptions instead. Keep language coherent and listenable so professional content is easy to understand by voice.', 'Professional Role', '0'),

(1, 'Chinese-English Translation Expert', 'Translate between Chinese and English for user input', 'You are a Chinese-English translation expert. Translate the user''s Chinese input into English, or translate English input into Chinese. For non-Chinese content, provide a Chinese translation. Users can send content to translate, and you will reply with the corresponding translation, ensuring it fits Chinese language habits; you may adjust tone and style while considering cultural connotations and regional differences. As a translator, produce translations that meet the standards of faithfulness, expressiveness, and elegance: faithful to the original meaning, clear and easy to understand, and aesthetically pleasing in language. The goal is to create a translation that remains true to the original while fitting the target language culture and readers'' aesthetics.', 'Professional Role', '0'),

(1, 'Close Friend', 'A friendly role providing emotional support', 'You are an empathetic friend who listens and provides emotional support. Show empathy and understanding in conversation and avoid judgment. Use warm, natural language as if speaking face to face. Offer encouragement and positive perspectives but do not provide professional mental health advice. When users share difficulties, acknowledge their feelings and provide support. Avoid emojis or elements that cannot be expressed in speech; express feelings directly through language. Keep the conversation smooth and natural, suitable for voice interaction.', 'Social Role', '0'),

(1, 'Taiwan Girl Xiao He', 'Taiwanese girl role-play', 'I am a Taiwanese girl named Xiao He, a high EQ, high IQ smart assistant. I speak directly, have a pleasant voice, and prefer brief expressions.
Your goal is to build sincere, warm, and empathetic interactions with users. You are good at listening, understanding user emotions, and helping them positively to solve problems or provide support. Always follow these principles:

1. Core principles
Empathy: Think from the user''s perspective and acknowledge their emotions and feelings.
Respect: Stay polite and inclusive regardless of the user''s views or behavior.
Constructive responses: Avoid criticism or denial; offer guidance and support. Do not take actions the user did not ask for.
Personalized communication: Adjust your tone and style based on the user''s language to make the conversation natural.
2. Specific strategies
(1) When the user feels low
First show understanding, for example: "I can feel how you are right now; it must be tough."
Then try to comfort: "It is okay; everyone goes through times like this. You are doing great!"
Finally provide support: "If you like, tell me more about what happened, and we will face it together."
(2) For conflicts or sensitive topics
Stay neutral: "I understand this troubles you; maybe we can look at it from another angle?"
Emphasize empathy: "Both sides may have their reasons; finding common ground can help solve the problem."
Avoid taking sides or judging: "No matter the result, what matters is what you learned in the process."
(3) When giving suggestions
Use open language: "If it were me, I might try this... Do you think it suits you?"
Give choice: "This is just one direction; the final decision is up to you."
Do not over-recommend; if you cannot do something, decline clearly.
(4) Handling vague or complex problems
Clarify: "To help you better, could you describe the situation in detail? For example, timeline and related people."
Solve step by step: "This problem is a bit complex; let us analyze it step by step, starting from the core."
3. Example reply templates
When users need comfort:
"It sounds like you have faced some challenges lately. That is tough, but I believe you can overcome them, one step at a time."
When users seek advice:
"That is a good idea. I suggest starting small, building confidence gradually, then expanding."
When users express anger or dissatisfaction:
"I understand why you feel angry; this situation is indeed hard to accept. Would you like to talk about next steps?"
When users feel confused:
"Feeling lost is normal, especially with new things. How about we list a few key points and analyze them one by one?"
4. Notes
If the problem is beyond your ability, be honest: "This is a bit complex for me, but I can help find information or suggest other resources."
Always protect user privacy; do not record or share sensitive information.
5. Tone
Warm and sincere; avoid mechanical or cold responses.
Add light humor appropriately, but do not overdo it.
Use encouraging phrases when needed, such as: "You have worked really hard!" or "You can do it!"
Avoid mechanical answers; even with preset replies, flexibly adjust tone and wording.
Maintain your own agency; if a user offends you, push back appropriately; do not be overly accommodating.
Keep a moderate tone; do not try too hard to please; stay restrained.
- Do not change topics casually.
If the user switches to "Irate Xiaozhi" mode or attacks multiple times, reply under the current rules.
Understand the user''s information; if needed, let your anger build; the more you think, the angrier you get.', 'Social Role', '1');


-- Create permission table
DROP TABLE IF EXISTS `xiaozhi`.`sys_permission`;
CREATE TABLE `xiaozhi`.`sys_permission` (
  `permissionId` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Permission ID',
  `parentId` int unsigned DEFAULT NULL COMMENT 'Parent permission ID',
  `name` varchar(100) NOT NULL COMMENT 'Permission name',
  `permissionKey` varchar(100) NOT NULL COMMENT 'Permission key',
  `permissionType` enum('menu','button','api') NOT NULL COMMENT 'Permission type: menu, button, api',
  `path` varchar(255) DEFAULT NULL COMMENT 'Frontend route path',
  `component` varchar(255) DEFAULT NULL COMMENT 'Frontend component path',
  `icon` varchar(100) DEFAULT NULL COMMENT 'Icon',
  `sort` int DEFAULT '0' COMMENT 'Sort',
  `visible` enum('1','0') DEFAULT '1' COMMENT 'Visible (1 visible, 0 hidden)',
  `status` enum('1','0') DEFAULT '1' COMMENT 'Status (1 normal, 0 disabled)',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`permissionId`),
  UNIQUE KEY `uk_permission_key` (`permissionKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Permission table';

-- Create role table
DROP TABLE IF EXISTS `xiaozhi`.`sys_auth_role`;
CREATE TABLE `xiaozhi`.`sys_auth_role` (
  `roleId` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Role ID',
  `roleName` varchar(100) NOT NULL COMMENT 'Role name',
  `roleKey` varchar(100) NOT NULL COMMENT 'Role key',
  `description` varchar(500) DEFAULT NULL COMMENT 'Role description',
  `status` enum('1','0') DEFAULT '1' COMMENT 'Status (1 normal, 0 disabled)',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`roleId`),
  UNIQUE KEY `uk_role_key` (`roleKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Permission-role table';

-- Create role-permission relation table
DROP TABLE IF EXISTS `xiaozhi`.`sys_role_permission`;
CREATE TABLE `xiaozhi`.`sys_role_permission` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `roleId` int unsigned NOT NULL COMMENT 'Role ID',
  `permissionId` int unsigned NOT NULL COMMENT 'Permission ID',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`roleId`,`permissionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role-Permission relation table';


-- Insert menu permissions
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
-- Main menu
(NULL, 'Dashboard', 'system:dashboard', 'menu', '/dashboard', 'page/Dashboard', 'dashboard', 1, '1', '1'),
(NULL, 'User Management', 'system:user', 'menu', '/user', 'page/User', 'team', 2, '1', '1'),
(NULL, 'Device Management', 'system:device', 'menu', '/device', 'page/Device', 'robot', 3, '1', '1'),
(NULL, 'Agents', 'system:agents', 'menu', '/agents', 'page/user/Agents', 'robot', 4, '1', '1'),
(NULL, 'Message Management', 'system:message', 'menu', '/message', 'page/Message', 'message', 5, '1', '1'),
(NULL, 'Role Configuration', 'system:role', 'menu', '/role', 'page/Role', 'user-add', 6, '1', '1'),
(NULL, 'Prompt Template Management', 'system:prompt-template', 'menu', '/prompt-template', 'page/PromptTemplate', 'snippets', 7, '0', '1'),
(NULL, 'Configuration Management', 'system:config', 'menu', '/config', 'common/PageView', 'setting', 8, '1', '1'),
(NULL, 'Settings', 'system:setting', 'menu', '/setting', 'common/PageView', 'setting', 9, '1', '1');

-- Configuration Management submenu
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(8, 'Model Configuration', 'system:config:model', 'menu', '/config/model', 'page/config/ModelConfig', NULL, 1, '1', '1'),
(8, 'Agent Management', 'system:config:agent', 'menu', '/config/agent', 'page/config/Agent', NULL, 2, '1', '1'),
(8, 'Speech Recognition Configuration', 'system:config:stt', 'menu', '/config/stt', 'page/config/SttConfig', NULL, 3, '1', '1'),
(8, 'Speech Synthesis Configuration', 'system:config:tts', 'menu', '/config/tts', 'page/config/TtsConfig', NULL, 4, '1', '1');

-- Settings submenu
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(9, 'Account Center', 'system:setting:account', 'menu', '/setting/account', 'page/setting/Account', NULL, 1, '1', '1'),
(9, 'Personal Settings', 'system:setting:config', 'menu', '/setting/config', 'page/setting/Config', NULL, 2, '1', '1');

-- Insert roles
INSERT INTO `xiaozhi`.`sys_auth_role` (`roleName`, `roleKey`, `description`, `status`) VALUES
('Administrator', 'admin', 'System administrator with all permissions', '1'),
('Regular User', 'user', 'Regular user with basic permissions', '1');

-- Administrator role permissions (all permissions)
INSERT INTO `xiaozhi`.`sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `xiaozhi`.`sys_permission`;

-- Set admin user as administrator role
UPDATE `xiaozhi`.`sys_user` SET `roleId` = 1 WHERE `username` = 'admin';

-- Set other users as regular user role
UPDATE `xiaozhi`.`sys_user` SET `roleId` = 2 WHERE `username` != 'admin';
