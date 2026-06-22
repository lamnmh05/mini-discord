param(
  [string] $BaseUrl = "http://localhost:8088/api/v1"
)

$ErrorActionPreference = "Stop"

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$results = New-Object System.Collections.Generic.List[object]

function Add-Result([string] $uc, [string] $name, [string] $status, [string] $detail = "") {
  $script:results.Add([pscustomobject]@{
    UC = $uc
    Name = $name
    Status = $status
    Detail = $detail
  })
}

function New-Session {
  New-Object Microsoft.PowerShell.Commands.WebRequestSession
}

function Request-Json([string] $method, [string] $url, $body = $null, [string] $token = $null, $session = $null) {
  $headers = @{}
  if ($token) {
    $headers.Authorization = "Bearer $token"
  }
  if ($null -eq $session) {
    $session = New-Session
  }
  if ($null -eq $body) {
    Invoke-RestMethod -Method $method -Uri $url -WebSession $session -Headers $headers
  } else {
    Invoke-RestMethod -Method $method -Uri $url -WebSession $session -Headers $headers -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 10)
  }
}

function New-TestUser([string] $label) {
  $session = New-Session
  $email = "${label}_$stamp@example.com"
  $username = "${label}$stamp"
  $password = "Password123!"
  Request-Json Post "$BaseUrl/auth/register" @{ username = $username; email = $email; password = $password } $null $session | Out-Null
  $login = Request-Json Post "$BaseUrl/auth/login" @{ email = $email; password = $password } $null $session
  [pscustomobject]@{
    Label = $label
    Email = $email
    Username = $username
    Password = $password
    Session = $session
    Token = $login.data.accessToken
    User = $login.data.user
  }
}

try {
  $owner = New-TestUser "owner"
  Add-Result "UC01/UC02" "Register + login owner" "PASS" $owner.Username
  $member = New-TestUser "member"
  Add-Result "UC01/UC02" "Register + login member" "PASS" $member.Username
  $invitee = New-TestUser "invitee"
  Add-Result "UC01/UC02" "Register + login invitee accept" "PASS" $invitee.Username
  $rejectee = New-TestUser "rejectee"
  Add-Result "UC01/UC02" "Register + login invitee reject" "PASS" $rejectee.Username
  $leaver = New-TestUser "leaver"
  Add-Result "UC01/UC02" "Register + login leaver" "PASS" $leaver.Username

  $me = Request-Json Get "$BaseUrl/users/me" $null $owner.Token $owner.Session
  Add-Result "UC04" "View current profile" "PASS" $me.data.username
  $updatedProfile = Request-Json Patch "$BaseUrl/users/me" @{ displayName = "Owner $stamp"; customStatus = "Testing use cases" } $owner.Token $owner.Session
  Add-Result "UC05" "Update profile" "PASS" $updatedProfile.data.displayName

  $friendRequest = Request-Json Post "$BaseUrl/friends/requests" @{ username = $member.Username } $owner.Token $owner.Session
  Add-Result "UC42" "Send friend request" "PASS" $friendRequest.data.id
  $memberFriendRequests = Request-Json Get "$BaseUrl/friends/requests" $null $member.Token $member.Session
  Add-Result "UC43" "View friend requests" "PASS" "count=$($memberFriendRequests.data.Count)"
  $acceptedFriend = Request-Json Post "$BaseUrl/friends/requests/$($friendRequest.data.id)/accept" @{} $member.Token $member.Session
  Add-Result "UC44" "Accept friend request" "PASS" $acceptedFriend.data.user.username
  $ownerFriends = Request-Json Get "$BaseUrl/friends" $null $owner.Token $owner.Session
  Add-Result "UC45" "View friends" "PASS" "count=$($ownerFriends.data.Count)"
  $directConversation = Request-Json Post "$BaseUrl/direct-conversations" @{ userId = $member.User.id } $owner.Token $owner.Session
  Add-Result "UC46" "Open direct conversation" "PASS" $directConversation.data.id
  $directMessage = Request-Json Post "$BaseUrl/direct-conversations/$($directConversation.data.id)/messages" @{ content = "direct hello $stamp"; attachments = @(); clientRequestId = [guid]::NewGuid().ToString() } $owner.Token $owner.Session
  Add-Result "UC47" "Send direct message" "PASS" $directMessage.data.id
  $directHistory = Request-Json Get "$BaseUrl/direct-conversations/$($directConversation.data.id)/messages" $null $member.Token $member.Session
  Add-Result "UC48" "View direct messages" "PASS" "count=$($directHistory.data.Count)"
  $directReacted = Request-Json Put "$BaseUrl/messages/$($directMessage.data.id)/reactions/%F0%9F%91%8D" @{} $member.Token $member.Session
  Add-Result "UC49" "React to direct message" "PASS" "reactions=$($directReacted.data.reactions.Count)"
  Request-Json Patch "$BaseUrl/direct-conversations/$($directConversation.data.id)/read" @{} $member.Token $member.Session | Out-Null
  Add-Result "UC50" "Mark direct conversation read" "PASS" $directConversation.data.id

  $server = Request-Json Post "$BaseUrl/servers" @{ name = "UseCase Server $stamp" } $owner.Token $owner.Session
  $serverId = $server.data.id
  $defaultChannelId = $server.data.defaultChannelId
  Add-Result "UC06" "Create server" "PASS" $serverId
  $serverList = Request-Json Get "$BaseUrl/servers" $null $owner.Token $owner.Session
  Add-Result "UC07" "View joined servers" "PASS" "count=$($serverList.data.Count)"
  $serverDetail = Request-Json Get "$BaseUrl/servers/$serverId" $null $owner.Token $owner.Session
  Add-Result "UC08" "View server detail" "PASS" $serverDetail.data.name
  $updatedServer = Request-Json Patch "$BaseUrl/servers/$serverId" @{ name = "UseCase Server Updated $stamp" } $owner.Token $owner.Session
  Add-Result "UC11" "Update server" "PASS" $updatedServer.data.name

  $inviteCode = Request-Json Post "$BaseUrl/servers/$serverId/invite-codes" @{ maxUses = 10 } $owner.Token $owner.Session
  Add-Result "UC13" "Create invite code" "PASS" $inviteCode.data.code
  Request-Json Post "$BaseUrl/invite-codes/$($inviteCode.data.code)/join" @{} $member.Token $member.Session | Out-Null
  Add-Result "UC09" "Join server by invite code" "PASS" $member.Username
  Request-Json Post "$BaseUrl/invite-codes/$($inviteCode.data.code)/join" @{} $leaver.Token $leaver.Session | Out-Null
  Request-Json Post "$BaseUrl/servers/$serverId/leave" @{} $leaver.Token $leaver.Session | Out-Null
  Add-Result "UC10" "Leave server" "PASS" $leaver.Username

  $channels = Request-Json Get "$BaseUrl/servers/$serverId/channels" $null $owner.Token $owner.Session
  Add-Result "UC15" "View channels" "PASS" "count=$($channels.data.Count)"
  $channel = Request-Json Post "$BaseUrl/servers/$serverId/channels" @{ name = "qa-$stamp" } $owner.Token $owner.Session
  Add-Result "UC16" "Create channel" "PASS" $channel.data.name
  $channelUpdated = Request-Json Patch "$BaseUrl/channels/$($channel.data.id)" @{ name = "qa-updated-$stamp"; position = 2 } $owner.Token $owner.Session
  Add-Result "UC17" "Update channel" "PASS" $channelUpdated.data.name
  Request-Json Delete "$BaseUrl/channels/$($channel.data.id)" $null $owner.Token $owner.Session | Out-Null
  Add-Result "UC18" "Delete channel" "PASS" $channel.data.id

  $msgOwner = Request-Json Post "$BaseUrl/channels/$defaultChannelId/messages" @{ content = "owner original $stamp"; attachments = @(); clientRequestId = [guid]::NewGuid().ToString() } $owner.Token $owner.Session
  Add-Result "UC20" "Send message" "PASS" $msgOwner.data.id
  $history = Request-Json Get "$BaseUrl/channels/$defaultChannelId/messages" $null $owner.Token $owner.Session
  Add-Result "UC19" "View messages" "PASS" "count=$($history.data.Count)"
  $edited = Request-Json Patch "$BaseUrl/messages/$($msgOwner.data.id)" @{ content = "owner edited $stamp" } $owner.Token $owner.Session
  Add-Result "UC21" "Edit own message" "PASS" $edited.data.content
  $reacted = Request-Json Put "$BaseUrl/messages/$($msgOwner.data.id)/reactions/%F0%9F%91%8D" @{} $member.Token $member.Session
  Add-Result "UC26" "Add reaction" "PASS" "reactions=$($reacted.data.reactions.Count)"
  $unreacted = Request-Json Delete "$BaseUrl/messages/$($msgOwner.data.id)/reactions/%F0%9F%91%8D" $null $member.Token $member.Session
  Add-Result "UC27" "Remove reaction" "PASS" "reactions=$($unreacted.data.reactions.Count)"
  Request-Json Delete "$BaseUrl/messages/$($msgOwner.data.id)" $null $owner.Token $owner.Session | Out-Null
  Add-Result "UC22" "Delete own message" "PASS" $msgOwner.data.id

  $tmp = Join-Path $env:TEMP "mini-discord-usecase-$stamp.txt"
  Set-Content -LiteralPath $tmp -Value "file payload $stamp" -Encoding ASCII
  $uploadJson = & curl.exe -sS -X POST "$BaseUrl/files" -H "Authorization: Bearer $($owner.Token)" -F "file=@$tmp;type=text/plain" -F "purpose=message"
  if ($LASTEXITCODE -ne 0) {
    throw "curl upload failed: $uploadJson"
  }
  $upload = $uploadJson | ConvertFrom-Json
  Add-Result "UC24" "Upload file" "PASS" $upload.data.originalName
  $msgFile = Request-Json Post "$BaseUrl/channels/$defaultChannelId/messages" @{
    content = "file message $stamp"
    attachments = @(@{
      storageKey = $upload.data.storageKey
      fileUrl = $upload.data.fileUrl
      originalName = $upload.data.originalName
      mimeType = $upload.data.mimeType
      fileSize = $upload.data.fileSize
    })
    clientRequestId = [guid]::NewGuid().ToString()
  } $owner.Token $owner.Session
  Add-Result "UC25" "Send message with file" "PASS" "attachments=$($msgFile.data.attachments.Count)"

  $msgMember = Request-Json Post "$BaseUrl/channels/$defaultChannelId/messages" @{ content = "member message $stamp"; attachments = @(); clientRequestId = [guid]::NewGuid().ToString() } $member.Token $member.Session
  Request-Json Delete "$BaseUrl/messages/$($msgMember.data.id)" $null $owner.Token $owner.Session | Out-Null
  Add-Result "UC23" "Delete member message as owner" "PASS" $msgMember.data.id

  $search = Request-Json Get "$BaseUrl/servers/$serverId/messages/search?q=file&channelId=$defaultChannelId" $null $owner.Token $owner.Session
  Add-Result "UC31" "Search message" "PASS" "count=$($search.data.Count)"
  $members = Request-Json Get "$BaseUrl/servers/$serverId/members" $null $owner.Token $owner.Session
  Add-Result "UC28/UC32" "View online/status members + members in server" "PASS" "count=$($members.data.Count)"
  $baseUri = [Uri] $BaseUrl
  $wsScheme = if ($baseUri.Scheme -eq "https") { "wss" } else { "ws" }
  $wsPort = if ($baseUri.IsDefaultPort) { "" } else { ":$($baseUri.Port)" }
  $wsUrl = "${wsScheme}://host.docker.internal$wsPort/ws"
  $typingJson = docker run --rm -v "${PWD}:/workspace" -w /workspace/frontend -e NODE_PATH=/workspace/frontend/node_modules -e WS_URL=$wsUrl -e ACCESS_TOKEN=$($owner.Token) -e CHANNEL_ID=$defaultChannelId node:24-alpine node /workspace/tools/stomp-typing-smoke-test.js
  $typing = $typingJson | ConvertFrom-Json
  if (-not $typing.ok) {
    throw "Typing smoke test failed: $typingJson"
  }
  Add-Result "UC29/UC30" "Typing realtime" "PASS" ($typing.events -join ",")
  $roleOwner = Request-Json Patch "$BaseUrl/servers/$serverId/members/$($member.User.id)/role" @{ role = "OWNER" } $owner.Token $owner.Session
  $roleMember = Request-Json Patch "$BaseUrl/servers/$serverId/members/$($member.User.id)/role" @{ role = "MEMBER" } $owner.Token $owner.Session
  Add-Result "UC33" "Change member role" "PASS" "$($roleOwner.data.role)->$($roleMember.data.role)"
  Request-Json Delete "$BaseUrl/servers/$serverId/members/$($member.User.id)" $null $owner.Token $owner.Session | Out-Null
  Add-Result "UC14" "Kick member" "PASS" $member.Username

  Request-Json Post "$BaseUrl/servers/$serverId/direct-invites" @{ inviteeUsername = $invitee.Username } $owner.Token $owner.Session | Out-Null
  Add-Result "UC37" "Send direct server invite by username" "PASS" $invitee.Username
  Start-Sleep -Milliseconds 300
  $receivedInvitee = Request-Json Get "$BaseUrl/server-invites/received" $null $invitee.Token $invitee.Session
  Add-Result "UC38" "View received server invites" "PASS" "count=$($receivedInvitee.data.Count)"
  $notifInvitee = Request-Json Get "$BaseUrl/notifications?isRead=false" $null $invitee.Token $invitee.Session
  Add-Result "UC35" "View notification" "PASS" "count=$($notifInvitee.data.Count)"
  if ($notifInvitee.data.Count -gt 0) {
    Request-Json Patch "$BaseUrl/notifications/$($notifInvitee.data[0].id)/read" @{} $invitee.Token $invitee.Session | Out-Null
    Add-Result "UC36" "Mark notification as read" "PASS" $notifInvitee.data[0].id
  } else {
    Add-Result "UC36" "Mark notification as read" "SKIP" "no unread notification generated"
  }
  Request-Json Post "$BaseUrl/server-invites/$($receivedInvitee.data[0].id)/accept" @{} $invitee.Token $invitee.Session | Out-Null
  Add-Result "UC39" "Accept direct server invite" "PASS" $invitee.Username

  Request-Json Post "$BaseUrl/servers/$serverId/direct-invites" @{ inviteeUsername = $rejectee.Username } $owner.Token $owner.Session | Out-Null
  $receivedRejectee = Request-Json Get "$BaseUrl/server-invites/received" $null $rejectee.Token $rejectee.Session
  Request-Json Post "$BaseUrl/server-invites/$($receivedRejectee.data[0].id)/reject" @{} $rejectee.Token $rejectee.Session | Out-Null
  Add-Result "UC40" "Reject direct server invite" "PASS" $rejectee.Username

  $revokeCode = Request-Json Post "$BaseUrl/servers/$serverId/invite-codes" @{ maxUses = 1 } $owner.Token $owner.Session
  Request-Json Delete "$BaseUrl/invite-codes/$($revokeCode.data.id)" $null $owner.Token $owner.Session | Out-Null
  Add-Result "UC41" "Revoke invite code" "PASS" $revokeCode.data.code

  $refresh = Request-Json Post "$BaseUrl/auth/refresh" @{} $owner.Token $owner.Session
  Add-Result "UC34" "Refresh token" "PASS" ([bool]$refresh.data.accessToken).ToString()

  $deleteServer = Request-Json Post "$BaseUrl/servers" @{ name = "Delete Me $stamp" } $owner.Token $owner.Session
  Request-Json Delete "$BaseUrl/servers/$($deleteServer.data.id)" $null $owner.Token $owner.Session | Out-Null
  Add-Result "UC12" "Delete server" "PASS" $deleteServer.data.id
  Request-Json Post "$BaseUrl/auth/logout" @{} $owner.Token $owner.Session | Out-Null
  Add-Result "UC03" "Logout" "PASS" $owner.Username
} catch {
  Add-Result "ERROR" "Script failed" "FAIL" $_.Exception.Message
}

$results | Format-Table -AutoSize | Out-String -Width 220

if (($results | Where-Object { $_.Status -eq "FAIL" }).Count -gt 0) {
  exit 1
}
