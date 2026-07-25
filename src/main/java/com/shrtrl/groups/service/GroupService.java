package com.shrtrl.groups.service;

@Service
public class GroupService {


    private GroupRepository groupRepository;

    public void save(Group group)
    {
        groupRepository.save(group);
    }

    public List<Group> getGroups()
    {
        return groupRepository.findAll();
    }

    @Query('DELETE FROM GROUPS WHERE :GROUPNAME=groupName')
    public void deletebyGroupName(String groupName)
    {
        groupRepository.deleteByGroupName(groupName);
    }

    public void deleteById(int groupid)
    {
        groupRepository.deleteById(groupid);
    }

    public boolean existsById(int groupid)
    {
       return groupRepository.existsById(groupid);
    }
}
