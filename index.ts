import { registerWidgetTaskHandler } from 'react-native-android-widget';
import { widgetTaskHandlerModule } from './widget-task-handler';
import { registerRootComponent } from 'expo';
import App from './App';
// registerRootComponent calls AppRegistry.registerComponent('main', () => App);
// It also ensures that whether you load the app in Expo Go or in a native build,
// the environment is set up appropriately
registerRootComponent(App);
// 🔥 注册小组件后台任务
// 这行代码必须在 AppRegistry.registerComponent 之前执行
// 或者直接放在文件最外层
registerWidgetTaskHandler(widgetTaskHandlerModule);