ALTER TABLE `xiaozhi`.`sys_user` ADD COLUMN `wxOpenId` VARCHAR(100) NULL COMMENT 'WeChat OpenId';
ALTER TABLE `xiaozhi`.`sys_user` ADD COLUMN `wxUnionId` VARCHAR(100) NULL COMMENT 'WeChat UnionId';
ALTER TABLE `xiaozhi`.`sys_user` ADD COLUMN `roleId` int unsigned NOT NULL DEFAULT 2 COMMENT 'Role ID';

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
