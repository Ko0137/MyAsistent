from kivy.app import App
from kivy.uix.label import Label

class JarvisApp(App):
    def build(self):
        return Label(text="J.A.R.V.I.S. UI Active")

if __name__ == '__main__':
    JarvisApp().run()
