import React, { useRef, useEffect, useState } from 'react';
import { View } from 'react-native';
import { WebView } from 'react-native-webview';
import * as FileSystem from 'expo-file-system/legacy'; 
import { Asset } from 'expo-asset';

interface OcrWebViewProps {
    imageUrl: string | null;
    onResult: (text: string) => void;
    onError: (err: string) => void;
    onProgress: (p: number) => void;
}

export const OcrHiddenWebView: React.FC<OcrWebViewProps> = ({ imageUrl, onResult, onError, onProgress }) => {
    const webViewRef = useRef<WebView>(null);
    const [htmlSource, setHtmlSource] = useState<string | null>(null);
    const [isWebViewReady, setIsWebViewReady] = useState(false);

    // 1. 初始化：读取本地 assets 中的 tesseract.min.js 内容，注入到 HTML 中
    useEffect(() => {
        const loadLocalResources = async () => {
            try {
                // 加载本地 JS 文件
                const tesseractAsset = Asset.fromModule(require('../../assets/tesseract.bin'));
                await tesseractAsset.downloadAsync(); // 确保资源已下载到本地缓存
                
                // 读取文件内容为字符串
                const libContent = await FileSystem.readAsStringAsync(tesseractAsset.localUri || tesseractAsset.uri);

                // 构建 HTML，直接把 JS 源码塞进去
                const html = `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>body { font-size: 12px; word-break: break-all; }</style>
                    <script>
                        // 注入本地 Tesseract 源码
                        ${libContent}
                    </script>
                </head>
                <body>
                    <div id="logs">Engine Loaded (Offline Mode)</div>
                    <script>
                        function log(msg) {
                            // document.getElementById('logs').innerText = msg; // 调试用
                            if (window.ReactNativeWebView) {
                                window.ReactNativeWebView.postMessage(JSON.stringify({type: 'log', message: msg}));
                            }
                        }

                        // 监听 RN 消息
                        document.addEventListener("message", handleMsg);
                        window.addEventListener("message", handleMsg);

                        function handleMsg(event) {
                            const data = event.data;
                            if (data && data.startsWith('data:image')) {
                                log('收到图片，开始工作...');
                                runOcr(data);
                            }
                        }

                        async function runOcr(imageBase64) {
                            try {
                                if (typeof Tesseract === 'undefined') {
                                    throw new Error('Tesseract 未加载');
                                }

                                const worker = await Tesseract.createWorker({
                                    logger: m => {
                                        if (m.status === 'recognizing text') {
                                            if (Math.round(m.progress * 100) % 10 === 0) {
                                                window.ReactNativeWebView.postMessage(JSON.stringify({type: 'progress', value: m.progress}));
                                            }
                                        }
                                    },
                                    // 🔥 关键：离线/缓存路径配置
                                    // 如果这里不配置，它默认还是会去 CDN 下载 worker.min.js 和 语言包
                                    // 我们先用 'eng' 测试，因为它比较小，容易缓存成功
                                });
                                
                                log('Worker 创建成功，加载语言包...');
                                
                                // 这里目前还是需要联网下载一次语言包
                                // 下载成功后，Tesseract.js 会自动缓存在浏览器的 IndexedDB 里
                                // 下次打开就不需要网了
                                await worker.loadLanguage('eng'); 
                                await worker.initialize('eng');
                                
                                log('开始识别...');
                                const ret = await worker.recognize(imageBase64);
                                
                                window.ReactNativeWebView.postMessage(JSON.stringify({type: 'success', text: ret.data.text}));
                                await worker.terminate();
                            } catch (error) {
                                log('错误: ' + error.toString());
                                window.ReactNativeWebView.postMessage(JSON.stringify({type: 'error', message: error.toString()}));
                            }
                        }
                        
                        // 通知 RN，HTML 加载完毕
                        setTimeout(function() {
                            log('WebView Ready');
                        }, 500);
                    </script>
                </body>
                </html>
                `;
                setHtmlSource(html);
            } catch (e) {
                console.error("加载本地资源失败:", e);
                onError("初始化离线引擎失败");
            }
        };

        loadLocalResources();
    }, []);

    // 2. 发送图片逻辑
    useEffect(() => {
        if (imageUrl && webViewRef.current && isWebViewReady) {
            console.log("发送图片给离线 WebView...");
            webViewRef.current.postMessage(imageUrl);
        }
    }, [imageUrl, isWebViewReady]);

    const handleMessage = (event: any) => {
        try {
            const data = JSON.parse(event.nativeEvent.data);
            if (data.type === 'log') {
                console.log("[离线OCR]:", data.message);
                if (data.message === 'WebView Ready' && !isWebViewReady) {
                    setIsWebViewReady(true);
                }
            } else if (data.type === 'success') {
                onResult(data.text);
            } else if (data.type === 'error') {
                onError(data.message);
            } else if (data.type === 'progress') {
                onProgress(data.value);
            }
        } catch (e) {}
    };

    if (!htmlSource) return <View />;

    return (
        <View style={{ height: 0, width: 0, overflow: 'hidden' }}>
            <WebView
                ref={webViewRef}
                originWhitelist={['*']}
                source={{ html: htmlSource, baseUrl: '' }}
                onMessage={handleMessage}
                javaScriptEnabled={true}
                domStorageEnabled={true}
                allowFileAccess={true}
                mixedContentMode="always"
            />
        </View>
    );
};