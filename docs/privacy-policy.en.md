# XianGeCe privacy policy

[简体中文](privacy-policy.md) · **English**

The data processing terms took effect on September 13, 2026. The edition information was updated on November 20, 2026. The Android package name is `win.iqwqi.xiangece`.

This policy describes how the app handles data. References to Android Keystore, Room, DataStore, foreground services and boot broadcasts describe the Android implementation. Courses, tasks, calendar notes and recordings stay on your device by default.

## 1. Information you provide

The app processes timetables, pictures, PDFs, HTML, spreadsheets and notification text you enter, select or share. It also processes the courses, tasks, events, calendar notes, habits, grades, financial records and settings you enter. It copies selected images to its private directory so you can refer back to the originals.

The app does not scan your photo library or read your contacts, call history, text messages, location or other apps' usage history. Optional local accounts and preferences are stored only on your device. A local account does not create an online account.

## 2. On-device recognition and classroom recording

The app uses the microphone only after you grant permission and start recording in Diting (谛听). On Android, a foreground service and system notification indicate that recording is in progress. You can pause, resume and end a recording. The original audio, transcript and markers are stored in the app's private directory by default.

After you install the corresponding offline resources, OCR and speech recognition run on the device. Availability of the system's on-device recognition depends on the device. You can keep the audio even when recognition is unavailable. Transcripts and suggested highlights can contain errors. You can replay the audio, check the text and make corrections. Before recording, observe your school's, your instructor's and any other applicable recording requirements.

## 3. Optional online features

Online assistance is off by default. The app contacts the service you specify only after you configure an endpoint, enable the feature and confirm as prompted. Each feature sends different material.

- **Text organization and draft enhancement** send the relevant notification text and the semester information needed for the task.
- **Note search and questions.** “Rephrase my search” (换个说法找) sends only the description of this search to expand its search terms. The search itself runs locally. “Organize selected content” (整理所选内容) and “Answer from selected material” (根据所选资料回答) send only the text excerpts you confirm and your current question. Before each request, the app shows the receiving address, model and a preview of the material. Image recognition requires separate confirmation. Questions and answers do not send the original images or recordings. Leaving the page or sending the app to the background cancels requests still in progress. Data already sent cannot be recalled.
- **Online image recognition** shows the receiving endpoint after you choose an image and asks for confirmation. If you agree, it sends the image and the prompts needed for recognition. You can choose local processing instead. With a text-only endpoint, the app sends only the locally recognized text after you confirm.
- **Classroom annotations** send completed transcript text, the classroom mode and relevant terminology. The annotation feature does not send the original audio.
- **Cloud classroom transcription** sends saved audio segments to the configured endpoint when online services are enabled, you have configured a transcription model and endpoint, and you turn on transcription for the session. The app tries the cloud service first. If that fails, it can use an installed local model. Opening the page alone does not upload audio. To keep audio entirely local, leave online transcription unconfigured or turn off online features. The app saves the original audio independently of recognition results.

Providers handle requests under their own terms. The receiving service gets the connection information needed for the request. Data goes directly to the endpoint you configure, without passing through a XianGeCe relay server. You can turn these features off. To request deletion of data already sent, contact the receiving provider.

On Android, endpoint keys are stored on the device using Android Keystore and AES-GCM encryption. Keys are excluded from source code, logs and exported backups.

## 4. Other network access and permissions

- When you choose to download or update an offline model, the app contacts the source listed on the page. The download service receives normal connection information.
- The relevant app handles external websites, sharing, system settings and the system installer when you open them.
- This Android preview has automatic update checks turned off.
- Notification and reminder permissions support courses, tasks, events and recording status. Microphone access is for recordings you start. File and image pickers access items you select for import. Android's boot broadcast restores reminders after a restart. Declining a permission limits the corresponding feature.
- You can use the app without cloud sync. The app does not automatically upload timetables, recordings or inbox items to a project server.

## 5. Storage, backup and deletion

On Android, app records are stored in a Room database by default. DataStore holds appearance and feature preferences. Android's automatic cloud backup is disabled.

Notes are stored locally as Markdown files. Image attachments, favorites, recovery drafts and edit history are also stored in the app's private directory. Local search and related-note suggestions do not send your text or search terms. Previews do not automatically load external images. After you tap and confirm an external link, the browser handles it. Collecting material for a note preserves a snapshot of the source text. For classroom sources, the app also stores the segments and playback positions. Editing or deleting the original classroom record leaves collected note snapshots in place. To remove a copy, manage the corresponding note as well.

You can export notes separately as Markdown or as a ZIP with images. An app backup also includes your current notes, source snapshots and attachments. Restoring notes merges them into a separate folder and preserves existing local notes. Text sources are retained. Classroom playback links require checking again. The app does not use old IDs to link to recordings on the device automatically. The export excludes the trash, unfinished collections, recovery drafts and previous versions. Before uninstalling, save or restore anything you want to keep and export it.

A `.xiangece` backup you explicitly export includes app records, appearance settings, inbox images, Diting transcripts and markers. It excludes endpoint keys and original classroom audio. Export recordings separately. When you share a backup, image or audio file, recipients can read it according to the sharing permissions you grant. Choose your recipients accordingly.

Restoring an app backup deletes the original recordings already on the device. Export any recordings you want to keep before restoring.

You can edit or delete records in the app. Deleting a classroom record attempts to delete its private audio files. Uninstalling the app or clearing its data removes app-private data. Uninstalling does not automatically delete copies you exported, shared or sent to a third-party service.

## 6. Minors, updates and contact

XianGeCe is intended for use on university campuses. Minors who use online services should review the provider's policies with a parent or guardian.

When data processing changes in a new version, the in-app information is updated. The complete policy for this edition ships with the app and can be read offline.

For privacy or licensing questions, use the [project contact page](https://iqwqi.win/cs/posts/xiangece/).
