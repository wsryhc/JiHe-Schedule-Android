// metro.config.js
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// 将 'bin' 添加到允许的资源后缀列表中
config.resolver.assetExts.push('bin');

module.exports = config;