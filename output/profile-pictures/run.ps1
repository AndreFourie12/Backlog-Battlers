$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -Path 'output/profile-pictures/format.ps1.cs' -ReferencedAssemblies System.Drawing
Get-ChildItem 'output/profile-pictures/source/ProfilePics' -File | ForEach-Object { $id=[int]($_.BaseName -replace 'pfp',''); [AvatarFormat]::Run($_.FullName,(Join-Path $pwd "output/profile-pictures/ready/pfp$id.png"),$id) }
Compress-Archive -Path 'output/profile-pictures/ready/*.png' -DestinationPath 'output/ProfilePics-Formatted.zip' -Force
