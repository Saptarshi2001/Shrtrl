package com.shrtrl.users.service;

@Service
public class UserService 
{

    private UserRepository userRepository;

    public void save(User user)
    {
        userRepository.save(user);
    }

    public List<User> getUsers()
    {
        return userRepository.findAll();
    }

    public void deletebyId(int id)
    {
        userRepository.deleteById(id);
    }

    @Query('UPDATE USER SET :PASSWORD=password WHERE :ID=userid')
    public void resetPassword(String password,int userid)
    {
        User user=userRepository.findById(userid);
        user.setPassword(password);
        userRepository.save(user);

    }

    public boolean existsById(int id)
    {
        return userRepository.existsById(id);
    }

}
