import os

main_xml = r'LibraryCard\src\main\res\layout\activity_main.xml'
with open(main_xml, 'r', encoding='utf-8') as f:
    lines = f.readlines()

home_xml = ['<?xml version="1.0" encoding="utf-8"?>\n'] + lines[16:234]
with open(r'LibraryCard\src\main\res\layout\fragment_home.xml', 'w', encoding='utf-8') as f:
    f.writelines(home_xml)

cards_xml = ['<?xml version="1.0" encoding="utf-8"?>\n'] + lines[236:315]
with open(r'LibraryCard\src\main\res\layout\fragment_cards.xml', 'w', encoding='utf-8') as f:
    f.writelines(cards_xml)

settings_xml = ['<?xml version="1.0" encoding="utf-8"?>\n'] + lines[317:383]
with open(r'LibraryCard\src\main\res\layout\fragment_settings.xml', 'w', encoding='utf-8') as f:
    f.writelines(settings_xml)

new_main = lines[:16] + [
    '        <androidx.fragment.app.FragmentContainerView\n',
    '            android:id="@+id/fragment_container"\n',
    '            android:layout_width="match_parent"\n',
    '            android:layout_height="match_parent" />\n'
] + lines[384:]

with open(main_xml, 'w', encoding='utf-8') as f:
    f.writelines(new_main)

print("XML Extracted Successfully")
