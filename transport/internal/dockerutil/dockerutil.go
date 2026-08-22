package dockerutil

import (
	"context"
	"os/exec"
	"strings"
)

func New(dataDir string) *DockerUtil {
	return &DockerUtil{dataDir: dataDir}
}

type DockerUtil struct {
	dataDir string
}

func (d *DockerUtil) AutoHeal(ctx context.Context) {
	<-ctx.Done()
}

func ServiceStatus() (smpStatus, xftpStatus string) {
	out, err := exec.Command("docker", "ps", "--filter", "name=ParanoidX-smp-server", "--format", "{{.Status}}").Output()
	if err == nil {
		smpStatus = strings.TrimSpace(string(out))
	}
	out, err = exec.Command("docker", "ps", "--filter", "name=ParanoidX-xftp-server", "--format", "{{.Status}}").Output()
	if err == nil {
		xftpStatus = strings.TrimSpace(string(out))
	}
	if smpStatus == "" {
		smpStatus = "Not running"
	}
	if xftpStatus == "" {
		xftpStatus = "Not running"
	}
	return
}

func ApplyStatus(smpStatus, xftpStatus *string, svc map[string]any) {
	if smpStatus != nil {
		svc["smp"] = *smpStatus
	}
	if xftpStatus != nil {
		svc["xftp"] = *xftpStatus
	}
}
