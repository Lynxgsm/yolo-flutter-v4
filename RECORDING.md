# Documentation technique : Paramètres d'enregistrement vidéo (VideoRecorder)

Ce document décrit les principaux paramètres utilisés pour l'enregistrement vidéo dans le module `VideoRecorder` (Android, MP4/H.264), ainsi que les raisons de leur choix pour obtenir un bon compromis entre qualité et taille de fichier.

---

## 1. Résolution (`setVideoSize`)

- **Définition** : La résolution détermine la taille (largeur x hauteur) de la vidéo en pixels.
- **Exemples courants** :
  - 1920x1080 (1080p, Full HD)
  - 1280x720 (720p, HD)
  - 854x480 (480p, SD)
- **Pourquoi ce choix ?**
  - Plus la résolution est élevée, meilleure est la qualité visuelle, mais la taille du fichier augmente.
  - 1080p est recommandé pour la plupart des usages modernes (YouTube, réseaux sociaux, etc.).

---

## 2. Débit binaire vidéo (`setVideoEncodingBitRate`)

- **Définition** : Quantité de données utilisée pour encoder chaque seconde de vidéo (en bits par seconde, bps).
- **Valeurs recommandées** :
  - 1080p : 6 à 10 Mbps (6 000 000 à 10 000 000)
  - 720p : 2,5 à 5 Mbps (2 500 000 à 5 000 000)
  - 480p : 1 à 2 Mbps (1 000 000 à 2 000 000)
- **Pourquoi ce choix ?**
  - Un débit plus élevé améliore la qualité mais augmente la taille du fichier.
  - 8 Mbps pour du 1080p offre un excellent compromis qualité/taille pour la plupart des usages.

---

## 3. Fréquence d'images (`setVideoFrameRate`)

- **Définition** : Nombre d'images par seconde (fps) enregistrées dans la vidéo.
- **Valeurs courantes** :
  - 30 fps (standard)
  - 60 fps (pour une grande fluidité, vidéos sportives, etc.)
- **Pourquoi ce choix ?**
  - 30 fps suffit pour la majorité des vidéos et limite la taille du fichier.
  - 60 fps double presque la taille du fichier, à réserver aux vidéos très dynamiques.

---

## 4. Format de sortie (`setOutputFormat` et `setVideoEncoder`)

- **Définition** :
  - `setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)` : Utilise le conteneur MP4, largement compatible.
  - `setVideoEncoder(MediaRecorder.VideoEncoder.H264)` : Utilise le codec H.264, standard pour la compression vidéo efficace.
- **Pourquoi ce choix ?**
  - MP4/H.264 est le couple le plus universellement supporté sur Android, iOS, web, etc.

---

## 5. Orientation (`setOrientationHint`)

- **Définition** : Permet de forcer l'orientation de la vidéo (portrait/paysage).
- **Pourquoi ce choix ?**
  - Garantit que la vidéo s'affiche correctement selon l'orientation de l'appareil.

---

## 6. Audio (optionnel)

- **Paramètres** :
  - `setAudioSource(MediaRecorder.AudioSource.MIC)`
  - `setAudioEncoder(MediaRecorder.AudioEncoder.AAC)`
- **Pourquoi ce choix ?**
  - Permet d'enregistrer le son du micro avec la vidéo.
  - AAC est un codec audio moderne, efficace et compatible.

---

## Résumé des paramètres recommandés pour un bon rapport qualité/taille

- **1080p** :
  - Résolution : 1920x1080
  - Bitrate : 8 Mbps
  - Framerate : 30 fps
- **720p** :
  - Résolution : 1280x720
  - Bitrate : 4 Mbps
  - Framerate : 30 fps

Adaptez ces valeurs selon vos besoins spécifiques (stockage, destination, fluidité souhaitée, etc.).
