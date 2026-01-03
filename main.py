from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.utils import platform
from android.permissions import request_permissions, Permission
import os

class EntryRunnerApp(App):
    def build(self):
        self.layout = BoxLayout(orientation='vertical', padding=20)
        self.label = Label(text="엔트리 파일 로더 초기화 중...", font_size='20sp')
        self.layout.add_widget(self.label)
        
        # 안드로이드 권한 요청 (저장소 읽기/쓰기)
        if platform == 'android':
            request_permissions([Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE])
            self.check_file()
        else:
            self.label.text = "안드로이드 환경이 아닙니다."
            
        return self.layout

    def check_file(self):
        # 스토리지 0 (내장 메모리 최상단) 경로 설정
        # 주의: 안드로이드 11 이상에서는 모든 파일 접근 권한이 더 엄격할 수 있습니다.
        file_path = "/storage/emulated/0/entry_project.ent"
        
        if os.path.exists(file_path):
            # 실제 실행을 위해서는 엔트리 엔진(JS) 파싱이 필요하므로, 여기서는 인식 성공 메시지만 출력
            self.label.text = f"파일 발견됨!\n경로: {file_path}\n\n(이 앱은 파일을 인식하지만,\n실제 구동에는 엔트리 엔진이 필요합니다.)"
        else:
            self.label.text = f"파일을 찾을 수 없습니다.\n\n내장 메모리 최상단에\n'entry_project.ent' 파일을 넣어주세요."

if __name__ == '__main__':
    EntryRunnerApp().run()
